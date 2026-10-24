defmodule Voy.World.Content do
  @moduledoc """
  The authoring pack: documents and claims, and the assembly that turns them
  into archive entries.

  Writers edit `content/` only. A document is one Markdown file with a flat
  front-matter header; a claim is one row of `content/claims.tsv`. The rules
  never learn about individual documents: they ask this module for the
  archives of a compiled history, and the pack decides which document fills
  which slot.

  Three guarantees this module is responsible for:

    * **no prose in code** — bodies live in the pack and may only interpolate
      registered placeholders (`{{place.down}}`, `{{date.era2a_zheng}}`);
    * **no ambiguous history** — for every slot and every applicable
      (fate, relic) combination exactly one document matches, so the archives
      stay a function of the history; a document may additionally pin the
      precedent it cites (`gazetteer_case`), which turns "the notebook quotes
      the gazetteer's case" from an author's promise into a selection
      constraint;
    * **author-facing errors** — `lint/1` reports `file:line` plus, where
      possible, the closest legal value, because the people editing this pack
      are not expected to read Elixir.

  `Voy.World.Projection.compile_archiveso/7` translates the loaded pack into
  relational goals directly; `archives_for/2` provides direct deterministic
  assembly for a compiled slice, and `preview/2` renders player-facing
  dossiers for writers without running the game.
  """

  alias Voy.World.{Schema, Timeline}

  @pack_dir Path.expand("../../../content", __DIR__)

  @relics [:ancestral_bronze_zheng, :carved_diversion_stele]
  @lenses [:official, :tech, :folk, :council]

  @governances [
    :brigade_elder_joint_council,
    :depopulated_concrete_sealed_zone,
    :rift_guarding_cliff_compact,
    :restored_ancestral_compact,
    :deferred_evacuation_watchpost,
    :cliff_observation_hermitage
  ]

  @gazetteer_cases [
    :case_1894_zheng_return_calmed_slide,
    :case_1902_stele_restored_stopped_flood,
    :case_mountain_too_steep_for_tithing
  ]

  # Per-slot contract: which fields are required, which are optional, and
  # which dimensions decide applicability. Dimensions not listed here are
  # rejected, so a typo cannot silently make a document match everything.
  @slots %{
    gazetteer: %{required: [:gazetteer_case, :title], optional: [], dims: [:gazetteer_case]},
    official_report: %{
      required: [:lens, :fate, :claim],
      optional: [:title, :gazetteer_case],
      dims: [:fate, :relic]
    },
    tech_note: %{
      required: [:lens, :fate, :claim],
      optional: [:relic, :title, :gazetteer_case],
      dims: [:fate, :relic]
    },
    council_document: %{
      required: [:fate, :governance, :title],
      optional: [],
      dims: [:fate]
    }
  }

  @slot_lenses %{official_report: [:official], tech_note: [:tech]}

  @claim_columns ["tag", "allowed_for", "forbidden_for", "label"]
  @front_matter_keys [
    "slot",
    "lens",
    "fate",
    "relic",
    "gazetteer_case",
    "claim",
    "governance",
    "title"
  ]

  @date_tokens %{
    "date.era2a_zheng" => {:reign_date, :guangxu, :era2a_zheng_case},
    "date.era2a_stele" => {:reign_date, :guangxu, :era2a_stele_case},
    "date.era2b" => {:year, :era2b_reclamation},
    "date.era2b_short" => {:short_year, :era2b_reclamation}
  }

  @place_roles ["up", "pass", "down"]

  defmodule Claim do
    @moduledoc "One causal assertion authors may cite from a document."
    defstruct [:id, :label, allowed_for: [], forbidden_for: [], source: %{}]
  end

  defmodule Document do
    @moduledoc "One authored document: applicability, citation, and body prose."
    defstruct [
      :id,
      :slot,
      :lens,
      :claim,
      :governance,
      :title,
      :body,
      fates: [],
      relics: [],
      gazetteer_cases: [],
      source: %{}
    ]
  end

  defmodule Pack do
    @moduledoc "A loaded content pack."
    defstruct claims: %{}, documents: [], dir: nil
  end

  @doc "Directory the pack is read from (override with `VOY_CONTENT_DIR`)."
  @spec pack_dir() :: Path.t()
  def pack_dir do
    case System.get_env("VOY_CONTENT_DIR") do
      nil -> @pack_dir
      dir -> Path.expand(dir)
    end
  end

  @doc "Slot names, for error messages and documentation."
  @spec slots() :: [atom()]
  def slots, do: Map.keys(@slots)

  @doc "Lens names a claim may allow or forbid."
  @spec lenses() :: [atom()]
  def lenses, do: @lenses

  @doc "Relic identities a tech note may be specialised for."
  @spec relics() :: [atom()]
  def relics, do: @relics

  @doc "Governance tags a council document may declare."
  @spec governances() :: [atom()]
  def governances, do: @governances

  @doc "Gazetteer precedents a gazetteer document may record."
  @spec gazetteer_cases() :: [atom()]
  def gazetteer_cases, do: @gazetteer_cases

  @doc "Placeholder tokens a body may use."
  @spec placeholders() :: [String.t()]
  def placeholders do
    date_tokens()
    |> Map.keys()
    |> Kernel.++(Enum.map(@place_roles, &"place.#{&1}"))
    |> Enum.sort()
  end

  @doc "Resolved date literals available to bodies."
  @spec date_tokens() :: %{String.t() => String.t()}
  def date_tokens do
    Map.new(@date_tokens, fn {token, form} -> {token, render_date(form)} end)
  end

  @doc "Load and validate the pack at `dir`."
  @spec load(Path.t()) :: {:ok, Pack.t()} | {:error, [map()]}
  def load(dir \\ pack_dir()) do
    dir = normalize_dir(dir)
    {claims, claim_issues} = read_claims(Path.join(dir, "claims.tsv"), dir)
    {documents, document_issues} = read_documents(Path.join(dir, "documents"), dir)

    pack = %Pack{
      claims: Map.new(claims, &{&1.id, &1}),
      documents: documents,
      dir: dir
    }

    issues =
      claim_issues ++
        document_issues ++
        validate_documents(pack) ++
        validate_coverage(pack)

    case issues do
      [] -> {:ok, pack}
      issues -> {:error, Enum.sort_by(issues, &{&1.file, &1.line})}
    end
  end

  @doc "Load the pack, raising with the formatted issues when it is invalid."
  @spec load!(Path.t()) :: Pack.t()
  def load!(dir \\ pack_dir()) do
    case load(dir) do
      {:ok, pack} ->
        pack

      {:error, issues} ->
        raise ArgumentError,
              "invalid content pack:\n" <> Enum.map_join(issues, "\n", &format_issue/1)
    end
  end

  @doc """
  Return the default authoring pack.

  When `VOY_CONTENT_DIR` is not set, the validated default pack is cached in
  `:persistent_term` so repeated relational queries do not re-parse files on
  every branch compilation.
  """
  @spec default_pack() :: Pack.t()
  def default_pack do
    case System.get_env("VOY_CONTENT_DIR") do
      nil ->
        key = {__MODULE__, :default_pack}

        case :persistent_term.get(key, nil) do
          %Pack{} = pack ->
            pack

          nil ->
            pack = load!(@pack_dir)
            :persistent_term.put(key, pack)
            pack
        end

      dir ->
        load!(dir)
    end
  end

  @doc "All documents in `pack` belonging to `slot`, in deterministic order."
  @spec documents_for(Pack.t(), atom()) :: [Document.t()]
  def documents_for(%Pack{documents: documents}, slot) when is_atom(slot) do
    Enum.filter(documents, &(&1.slot == slot))
  end

  @doc "All claim tag atoms that `lens` is forbidden to cite."
  @spec forbidden_claims_for(Pack.t(), atom()) :: [atom()]
  def forbidden_claims_for(%Pack{claims: claims}, lens) when is_atom(lens) do
    claims
    |> Map.values()
    |> Enum.filter(&(lens in &1.forbidden_for))
    |> Enum.map(&tag_atom(&1.id))
    |> Enum.sort()
  end

  @doc "Render a document body given a `%{up: ..., pass: ..., down: ...}` name map."
  @spec render_body(String.t(), map()) :: String.t()
  def render_body(body, names) when is_binary(body) and is_map(names) do
    render(body, bindings(names))
  end

  @doc "Lint the pack; errors block a merge, warnings do not."
  @spec lint(Path.t()) :: %{errors: [map()], warnings: [map()]}
  def lint(dir \\ pack_dir()) do
    case load(dir) do
      {:ok, pack} ->
        %{errors: [], warnings: warnings(pack)}

      {:error, issues} ->
        %{errors: issues, warnings: []}
    end
  end

  @doc "Assemble the archive entries of one compiled history slice."
  @spec archives_for(Keyword.t(), Pack.t()) :: {:ok, Keyword.t()} | {:error, term()}
  def archives_for(slice, pack \\ nil) do
    pack = pack || load!()
    topo = Keyword.fetch!(slice, :topography)
    names = %{up: topo[:up_name], pass: topo[:pass_name], down: topo[:down_name]}
    fate = slice |> Keyword.fetch!(:era3) |> Keyword.fetch!(:fate)
    relic = slice |> Keyword.fetch!(:era1) |> Keyword.fetch!(:guardian_relic)

    gazetteer_case =
      slice |> Keyword.fetch!(:era2a) |> Keyword.fetch!(:gazetteer_precedent)

    bindings = bindings(names)

    with {:ok, gazetteer} <- select(pack, :gazetteer, gazetteer_case: gazetteer_case),
         {:ok, official} <-
           select(pack, :official_report,
             fate: fate,
             relic: relic,
             gazetteer_case: gazetteer_case,
             lens: :official
           ),
         {:ok, tech} <-
           select(pack, :tech_note,
             fate: fate,
             relic: relic,
             gazetteer_case: gazetteer_case,
             lens: :tech
           ),
         {:ok, council} <- select(pack, :council_document, fate: fate) do
      {:ok,
       [
         gazetteer: [
           title: gazetteer.title,
           recorded_settlement: names.down,
           precedent: gazetteer_case,
           excerpt: render(gazetteer.body, bindings)
         ],
         official_report: [
           target_settlement: names.down,
           upstream_site: names.up,
           claimed_cause: tag_atom(official.claim),
           summary: render(official.body, bindings)
         ],
         tech_note: [
           true_source: names.up,
           settlement: names.down,
           corroborated_gazetteer: corroborated_case(tech, gazetteer_case),
           true_cause: tag_atom(tech.claim),
           note: render(tech.body, bindings)
         ],
         council_document: [
           title: council.title,
           settlement: names.down,
           governance: governance_atom(council.governance),
           decree: render(council.body, bindings)
         ]
       ]}
    end
  end

  @doc "Render one body, resolving placeholders; unknown tokens raise."
  @spec render(String.t(), Keyword.t()) :: String.t()
  def render(body, bindings) when is_binary(body) do
    rendered =
      Enum.reduce(bindings, body, fn {name, value}, acc ->
        String.replace(acc, "{{#{name}}}", to_string(value))
      end)

    case Regex.run(~r/\{\{([a-z0-9_.]+)\}\}/, rendered) do
      nil ->
        rendered

      [token, name] ->
        raise ArgumentError,
              "unresolved placeholder {{#{name}}} (#{token}); available: " <>
                Enum.join(placeholders(), ", ")
    end
  end

  @doc "Claim tag atom of a claim id, e.g. `claim.rain` -> `:rain`."
  @spec tag_atom(String.t()) :: atom()
  def tag_atom("claim." <> tag), do: String.to_atom(tag)

  @doc "Governance atom of a declared governance id."
  @spec governance_atom(String.t() | atom()) :: atom()
  def governance_atom(id) when is_atom(id), do: id
  def governance_atom(id), do: String.to_existing_atom(id)

  @doc "Human-readable one-line form of a lint issue."
  @spec format_issue(map()) :: String.t()
  def format_issue(%{file: file, line: line, message: message}) do
    "#{file}:#{line}: #{message}"
  end

  @preview_presets [
    {"baishui",
     [
       branch_id: :branch_01_baishui,
       up_name: "野猪箐裂隙",
       pass_name: "断碑垭口",
       down_name: "白水坝",
       up_terrain: :steep_fissure_headwater,
       pass_terrain: :canyon_pass,
       down_terrain: :alluvial_flat,
       up_geology: :active_fault_vein,
       accessibility: :tractor_road_accessible
     ]},
    {"heishui",
     [
       branch_id: :branch_02_heishui,
       up_name: "鬼哭漏斗",
       pass_name: "锁龙峡",
       down_name: "黑水坪",
       up_terrain: :karst_sinkhole_headwater,
       pass_terrain: :karst_gorge,
       down_terrain: :alluvial_flat,
       up_geology: :deep_stress_cavity,
       accessibility: :tractor_road_accessible
     ]},
    {"xuanguan",
     [
       branch_id: :branch_04_xuanguan,
       up_name: "鬼哭漏斗",
       pass_name: "断碑垭口",
       down_name: "悬棺寨",
       up_terrain: :karst_sinkhole_headwater,
       pass_terrain: :canyon_pass,
       down_terrain: :mid_terrace,
       up_geology: :deep_stress_cavity,
       accessibility: :cliff_isolated
     ]},
    {"nuoliang",
     [
       branch_id: :branch_03_nuoliang,
       up_name: "老君林场",
       pass_name: "响水涧口",
       down_name: "糯良古寨",
       up_terrain: :steep_fissure_headwater,
       pass_terrain: :canyon_pass,
       down_terrain: :mid_terrace,
       up_geology: :minor_fracture,
       accessibility: :cliff_isolated
     ]},
    {"mahao",
     [
       branch_id: :branch_05_mahao,
       up_name: "蚂蟥窠裂隙",
       pass_name: "老熊垭口",
       down_name: "水冬瓜坝",
       up_terrain: :steep_fissure_headwater,
       pass_terrain: :canyon_pass,
       down_terrain: :alluvial_flat,
       up_geology: :minor_fracture,
       accessibility: :tractor_road_accessible
     ]},
    {"tingshan",
     [
       branch_id: :branch_06_tingshan,
       up_name: "响水箐头",
       pass_name: "垮岩垭口",
       down_name: "听山崖寨",
       up_terrain: :steep_fissure_headwater,
       pass_terrain: :canyon_pass,
       down_terrain: :mid_terrace,
       up_geology: :active_fault_vein,
       accessibility: :cliff_isolated
     ]}
  ]

  @preview_aliases %{
    "assimilated" => "baishui",
    "Fate_Assimilated_NewZhai" => "baishui",
    "annihilated" => "heishui",
    "Fate_Annihilated_BurnoutZone" => "heishui",
    "ascended" => "xuanguan",
    "rift" => "xuanguan",
    "Fate_Ascended_SpatialRift" => "xuanguan",
    "ancestral" => "nuoliang",
    "Fate_Ancestral_ControlGroup" => "nuoliang",
    "seep" => "mahao",
    "chronic_seep" => "mahao",
    "Fate_Deferred_ChronicSeep" => "mahao",
    "falsealarm" => "tingshan",
    "false_alarm" => "tingshan",
    "Fate_Tolerated_FalseAlarm" => "tingshan"
  }

  @doc "Named presets available to `preview/1`."
  @spec preview_presets() :: [String.t()]
  def preview_presets, do: Enum.map(@preview_presets, &elem(&1, 0))

  @doc """
  Render player-facing dossier text for `"all"` or one preset (`"baishui"`,
  `"heishui"`, `"xuanguan"`, `"nuoliang"`, `"mahao"`, `"tingshan"`, or a fate
  alias).
  """
  @spec preview(String.t() | atom()) :: {:ok, String.t()} | {:error, String.t()}
  def preview(target \\ "all") do
    target = to_string(target)
    canonical = Map.get(@preview_aliases, target, target)

    cond do
      canonical == "all" ->
        rendered =
          @preview_presets
          |> Enum.map(fn {name, topo} -> format_preset_preview(name, topo) end)
          |> Enum.join("\n\n")

        {:ok, rendered}

      topo = List.keyfind(@preview_presets, canonical, 0) |> then(&(&1 && elem(&1, 1))) ->
        {:ok, format_preset_preview(canonical, topo)}

      true ->
        candidates = preview_presets() ++ ["all" | Map.keys(@preview_aliases)]

        {:error,
         "未知预览节点 #{inspect(target)}；可用预设：#{Enum.join(preview_presets() ++ ["all"], " / ")}" <>
           did_you_mean(target, candidates)}
    end
  end

  @doc "Starter Markdown template for an authoring slot."
  @spec template(String.t() | atom()) :: {:ok, String.t()} | {:error, String.t()}
  def template(slot) do
    slot_str = to_string(slot)

    case enum_value(slot_str, Map.keys(@slots)) do
      {:ok, :gazetteer} ->
        {:ok,
         """
         ---
         slot: gazetteer
         # 可选先例：#{Enum.join(@gazetteer_cases, " / ")}
         gazetteer_case: case_1894_zheng_return_calmed_slide
         title: 《旧州府抚夷清丈残卷·某案》
         ---

         {{date.era2a_zheng}}于{{place.down}}……
         """}

      {:ok, :official_report} ->
        {:ok,
         """
         ---
         slot: official_report
         lens: official
         # 可选结局：#{Enum.join(Schema.fates(), " / ")}
         fate: Fate_Assimilated_NewZhai
         # 可选：钉死引用的先例（不写则通配）：#{Enum.join(@gazetteer_cases, " / ")}
         # gazetteer_case: case_1894_zheng_return_calmed_slide
         # 引用 claims.tsv 中 allowed_for 含 official 的归因卡
         claim: claim.seasonal_rain_mudslide
         ---

         归咎于……
         """}

      {:ok, :tech_note} ->
        {:ok,
         """
         ---
         slot: tech_note
         lens: tech
         # 可选结局：#{Enum.join(Schema.fates(), " / ")}
         fate: Fate_Assimilated_NewZhai
         # 可选古器（不写则通配）：#{Enum.join(@relics, " / ")}
         relic: ancestral_bronze_zheng
         # 可选：钉死引用的先例（不写则通配）：#{Enum.join(@gazetteer_cases, " / ")}
         # gazetteer_case: case_1894_zheng_return_calmed_slide
         # 引用 claims.tsv 中 allowed_for 含 tech 的归因卡
         claim: claim.c0_removed_and_vulnerable_window_blasting
         ---

         对照清丈残卷，{{date.era2b_short}}年……
         """}

      {:ok, :council_document} ->
        {:ok,
         """
         ---
         slot: council_document
         # 可选结局：#{Enum.join(Schema.fates(), " / ")}
         fate: Fate_Assimilated_NewZhai
         # 可选政体：#{Enum.join(@governances, " / ")}
         governance: brigade_elder_joint_council
         title: 《某规约》
         ---

         凡……
         """}

      :error ->
        allowed = Enum.map(Map.keys(@slots), &to_string/1)

        {:error,
         "未知档案槽位 #{inspect(slot_str)}；可用：#{Enum.join(allowed, " / ")}" <>
           did_you_mean(slot_str, allowed)}
    end
  end

  defp format_preset_preview(name, topo) do
    {:ok, [slice]} = Voy.World.compile_branch(topo)
    structures = slice["structures"]
    archives = slice["archives"]
    gazetteer = archives["gazetteer"]
    official = archives["official_report"]
    tech = archives["tech_note"]
    council = archives["council_document"]
    [up_site, up_desc] = structures["upstream_site"]
    [pass_site, pass_desc] = structures["pass_site"]
    [down_site, down_desc] = structures["downstream_site"]

    """
    ==================================================================
    【节点档案预览：#{name}（#{topo[:down_name]}）】
    支沟：#{topo[:up_name]} → #{topo[:pass_name]} → #{topo[:down_name]}
    结局：#{slice["era3"]["fate"]}（慢变量分区：#{slice["era3"]["final_regime"]}）
    古器：#{slice["era1"]["guardian_relic"]}
    ------------------------------------------------------------------
    [聚落与遗迹落位]
    · 源头（#{up_site}）：#{up_desc}
    · 隘口（#{pass_site}）：#{pass_desc}
    · 聚落（#{down_site}）：#{down_desc}
    ------------------------------------------------------------------
    ① 旧州府残卷：#{gazetteer["title"]}（#{gazetteer["precedent"]}）
       #{gazetteer["excerpt"]}

    ② 垦殖总场官样通报（口径归因：#{official["claimed_cause"]}）
       #{official["summary"]}

    ③ 技术员手簿批注（真实归因：#{tech["true_cause"]}，印证：#{tech["corroborated_gazetteer"]}）
       #{tech["note"]}

    ④ 第三纪规约：#{council["title"]}（政体：#{council["governance"]}）
       #{council["decree"]}
    ==================================================================\
    """
  end

  # ==== claims ==== //

  defp read_claims(path, dir) do
    case File.read(path) do
      {:error, reason} ->
        {[], [issue(path, dir, 0, "无法读取归因卡表：#{:file.format_error(reason)}")]}

      {:ok, content} ->
        parse_claims(path, dir, content)
    end
  end

  defp parse_claims(path, dir, content) do
    case table_rows(content) do
      [] ->
        {[], [issue(path, dir, 1, "归因卡表是空的")]}

      [{header, header_line} | body] ->
        case claim_columns(split_row(header), path, dir, header_line) do
          {:ok, index} ->
            body
            |> Enum.reduce({[], []}, fn {row, line}, {claims, issues} ->
              case parse_claim(index, split_row(row), path, dir, line) do
                {:ok, claim} -> {[claim | claims], issues}
                {:error, issue} -> {claims, [issue | issues]}
              end
            end)
            |> then(fn {claims, issues} -> {Enum.reverse(claims), Enum.reverse(issues)} end)

          {:error, issue} ->
            {[], [issue]}
        end
    end
  end

  defp claim_columns(columns, path, dir, line) do
    unknown = Enum.reject(columns, &(&1 in @claim_columns))
    missing = Enum.reject(@claim_columns, &(&1 in columns))
    duplicated = columns -- Enum.uniq(columns)

    cond do
      unknown != [] ->
        suggestion =
          unknown
          |> Enum.map(&did_you_mean(&1, @claim_columns))
          |> Enum.find("", &(&1 != ""))

        {:error,
         issue(
           path,
           dir,
           line,
           "表头有未知列：#{Enum.join(unknown, ", ")}；可用列：#{Enum.join(@claim_columns, ", ")}" <>
             suggestion
         )}

      duplicated != [] ->
        {:error, issue(path, dir, line, "表头有重复列：#{Enum.join(duplicated, ", ")}")}

      missing != [] ->
        {:error, issue(path, dir, line, "表头缺少列：#{Enum.join(missing, ", ")}")}

      true ->
        {:ok, columns |> Enum.with_index() |> Map.new()}
    end
  end

  defp parse_claim(index, cells, path, dir, line) do
    if length(cells) != map_size(index) do
      {:error,
       issue(
         path,
         dir,
         line,
         "这一行应有 #{map_size(index)} 列（#{Enum.join(@claim_columns, " / ")}），实际 #{length(cells)} 列"
       )}
    else
      tag = Enum.at(cells, index["tag"])
      label = Enum.at(cells, index["label"])

      with :ok <- check_tag(tag, path, dir, line),
           {:ok, allowed_for} <-
             parse_lenses(Enum.at(cells, index["allowed_for"]), "allowed_for", path, dir, line),
           {:ok, forbidden_for} <-
             parse_lenses(
               Enum.at(cells, index["forbidden_for"]),
               "forbidden_for",
               path,
               dir,
               line
             ),
           :ok <- check_lens_taboo(allowed_for, forbidden_for, tag, path, dir, line) do
        {:ok,
         %Claim{
           id: tag,
           label: label,
           allowed_for: allowed_for,
           forbidden_for: forbidden_for,
           source: %{file: relative(path, dir), line: line}
         }}
      end
    end
  end

  defp check_tag(tag, path, dir, line) do
    if Regex.match?(~r/^claim\.[a-z][a-z0-9_]*$/, tag) do
      :ok
    else
      {:error, issue(path, dir, line, "归因卡 id 必须形如 claim.latin_slug，实际是 #{inspect(tag)}")}
    end
  end

  defp parse_lenses("", _field, _path, _dir, _line), do: {:ok, []}

  defp parse_lenses(cell, field, path, dir, line) do
    values = split_list(cell)

    Enum.reduce_while(values, {:ok, []}, fn value, {:ok, acc} ->
      case enum_value(value, @lenses) do
        {:ok, lens} ->
          {:cont, {:ok, [lens | acc]}}

        :error ->
          {:halt,
           {:error,
            issue(
              path,
              dir,
              line,
              "#{field} 里的 #{inspect(value)} 不是合法口径；可用：#{Enum.join(@lenses, ", ")}" <>
                did_you_mean(value, @lenses)
            )}}
      end
    end)
    |> case do
      {:ok, lenses} -> {:ok, Enum.reverse(lenses)}
      {:error, _issue} = error -> error
    end
  end

  defp check_lens_taboo(allowed, forbidden, tag, path, dir, line) do
    case Enum.filter(allowed, &(&1 in forbidden)) do
      [] ->
        :ok

      overlap ->
        {:error,
         issue(
           path,
           dir,
           line,
           "#{tag} 的 allowed_for 与 forbidden_for 同时包含 #{Enum.join(overlap, ", ")}"
         )}
    end
  end

  # ==== documents ==== //

  defp read_documents(dir, pack_dir) do
    paths = Path.wildcard(Path.join(dir, "**/*.md")) |> Enum.sort()

    case paths do
      [] ->
        {[], [issue(dir, pack_dir, 0, "没有找到任何档案卡（documents/**/*.md）")]}

      paths ->
        Enum.reduce(paths, {[], []}, fn path, {documents, issues} ->
          case read_document(path, pack_dir, dir) do
            {:ok, document} -> {[document | documents], issues}
            {:error, issue} -> {documents, [issue | issues]}
          end
        end)
        |> then(fn {documents, issues} ->
          {Enum.reverse(documents), Enum.reverse(issues)}
        end)
    end
  end

  defp read_document(path, pack_dir, documents_dir) do
    case File.read(path) do
      {:error, reason} ->
        {:error, issue(path, pack_dir, 0, "无法读取档案卡：#{:file.format_error(reason)}")}

      {:ok, content} ->
        with {:ok, header_lines, body, body_line} <- split_document(content, path, pack_dir),
             {:ok, fields} <- parse_header(header_lines, path, pack_dir),
             :ok <- check_slot_fields(fields, path, pack_dir),
             {:ok, document} <-
               build_document(fields, body, path, pack_dir, documents_dir, body_line) do
          {:ok, document}
        end
    end
  end

  defp split_document(content, path, pack_dir) do
    content = content |> String.trim_leading("\uFEFF") |> String.replace("\r\n", "\n")

    case String.split(content, "\n") do
      [first | rest] ->
        if String.trim(first) == "---" do
          split_body(rest, path, pack_dir)
        else
          {:error, issue(path, pack_dir, 1, "档案卡必须以 --- 开头")}
        end

      _other ->
        {:error, issue(path, pack_dir, 1, "档案卡是空的")}
    end
  end

  defp split_body(rest, path, pack_dir) do
    case Enum.find_index(rest, &(String.trim(&1) == "---")) do
      nil ->
        {:error, issue(path, pack_dir, 1, "档案卡缺少结束的 ---（头部与正文之间）")}

      index ->
        header_lines = rest |> Enum.take(index) |> Enum.with_index(2)
        body = rest |> Enum.drop(index + 1) |> Enum.join("\n") |> String.trim()
        {:ok, header_lines, body, index + 3}
    end
  end

  defp parse_header(header_lines, path, pack_dir) do
    Enum.reduce_while(header_lines, {:ok, %{}}, fn {line, number}, {:ok, acc} ->
      trimmed = String.trim(line)

      cond do
        trimmed == "" or String.starts_with?(trimmed, "#") ->
          {:cont, {:ok, acc}}

        true ->
          case String.split(trimmed, ":", parts: 2) do
            [key, value] ->
              key = String.trim(key)

              if key in @front_matter_keys do
                {:cont, {:ok, Map.put(acc, key, String.trim(value))}}
              else
                {:halt,
                 {:error,
                  issue(
                    path,
                    pack_dir,
                    number,
                    "未知字段 #{inspect(key)}；可用字段：#{Enum.join(@front_matter_keys, " / ")}" <>
                      did_you_mean(key, @front_matter_keys)
                  )}}
              end

            _other ->
              {:halt,
               {:error,
                issue(path, pack_dir, number, "字段行必须是 key: value，实际是 #{inspect(trimmed)}")}}
          end
      end
    end)
  end

  defp check_slot_fields(fields, path, pack_dir) do
    slot = Map.get(fields, "slot")

    case enum_value(slot, Map.keys(@slots)) do
      :error ->
        allowed = Enum.map(Map.keys(@slots), &to_string/1)

        {:error,
         issue(
           path,
           pack_dir,
           2,
           "slot 必须是 #{Enum.join(allowed, " / ")}，实际是 #{inspect(slot)}" <>
             did_you_mean(slot, allowed)
         )}

      {:ok, slot} ->
        spec = Map.fetch!(@slots, slot)
        present = Enum.map(Map.keys(fields), &String.to_existing_atom/1)
        missing = spec.required -- present

        cond do
          missing != [] ->
            {:error,
             issue(
               path,
               pack_dir,
               2,
               "#{slot} 缺少必填字段：#{Enum.map_join(missing, ", ", &to_string/1)}"
             )}

          true ->
            check_unknown_dimensions(slot, present, spec, path, pack_dir)
        end
    end
  end

  defp check_unknown_dimensions(slot, present, spec, path, pack_dir) do
    allowed = spec.required ++ spec.optional ++ [:slot]

    case Enum.reject(present, &(&1 in allowed)) do
      [] ->
        :ok

      extra ->
        {:error,
         issue(
           path,
           pack_dir,
           2,
           "#{slot} 不接受字段：#{Enum.map_join(extra, ", ", &to_string/1)}"
         )}
    end
  end

  defp build_document(fields, body, path, pack_dir, documents_dir, body_line) do
    body = String.trim(body)

    with :ok <- check_body(body, path, pack_dir, body_line),
         :ok <- check_placeholders(body, path, pack_dir, body_line),
         :ok <- check_hardcoded_dates(body, path, pack_dir, body_line),
         {:ok, slot} <- enum_value(fields["slot"], Map.keys(@slots)),
         {:ok, lens} <- parse_optional_enum(fields["lens"], @lenses, "lens", path, pack_dir),
         :ok <- check_slot_lens(slot, lens, path, pack_dir),
         {:ok, fates} <- parse_enum_list(fields["fate"], Schema.fates(), "fate", path, pack_dir),
         {:ok, relics} <- parse_enum_list(fields["relic"], @relics, "relic", path, pack_dir),
         {:ok, cases} <-
           parse_enum_list(
             fields["gazetteer_case"],
             @gazetteer_cases,
             "gazetteer_case",
             path,
             pack_dir
           ),
         {:ok, governance} <-
           parse_optional_enum(fields["governance"], @governances, "governance", path, pack_dir) do
      {:ok,
       %Document{
         id: document_id(path, documents_dir),
         slot: slot,
         lens: lens,
         fates: fates,
         relics: relics,
         gazetteer_cases: cases,
         claim: fields["claim"],
         governance: governance,
         title: fields["title"],
         body: body,
         source: %{file: relative(path, pack_dir), line: body_line}
       }}
    end
  end

  defp check_body("", path, pack_dir, line),
    do: {:error, issue(path, pack_dir, line, "正文是空的")}

  defp check_body(_body, _path, _pack_dir, _line), do: :ok

  defp check_slot_lens(slot, lens, path, pack_dir) do
    case Map.fetch(@slot_lenses, slot) do
      :error ->
        :ok

      {:ok, allowed} ->
        if lens in allowed do
          :ok
        else
          {:error,
           issue(
             path,
             pack_dir,
             2,
             "#{slot} 的 lens 必须是 #{Enum.join(allowed, ", ")}，实际是 #{inspect(lens)}"
           )}
        end
    end
  end

  defp check_placeholders(body, path, pack_dir, line) do
    allowed = placeholders()

    unknown =
      ~r/\{\{([a-z0-9_.]+)\}\}/
      |> Regex.scan(body)
      |> Enum.map(fn [_token, name] -> name end)
      |> Enum.uniq()
      |> Enum.reject(&(&1 in allowed))

    case unknown do
      [] ->
        :ok

      tokens ->
        suggestion =
          tokens
          |> Enum.map(fn token -> did_you_mean(token, allowed, &"{{#{&1}}}") end)
          |> Enum.find("", &(&1 != ""))

        {:error,
         issue(
           path,
           pack_dir,
           line,
           "无法解析的占位符：#{Enum.map_join(tokens, ", ", &"{{#{&1}}}")}；可用：#{Enum.join(allowed, ", ")}" <>
             suggestion
         )}
    end
  end

  defp check_hardcoded_dates(body, path, pack_dir, line) do
    stripped = Regex.replace(~r/\{\{[a-z0-9_.]+\}\}/, body, "")

    found =
      [
        ~r/光绪[一二三四五六七八九十零]+年/,
        ~r/(?<!\d)\d{4}年/,
        ~r/(?<!\d)(?:18|19|20)\d{2}(?!\d)/,
        ~r/(?<!\d)\d{2}年/
      ]
      |> Enum.flat_map(&Regex.scan(&1, stripped))
      |> List.flatten()
      |> Enum.uniq()

    case found do
      [] ->
        :ok

      literals ->
        date_placeholders =
          @date_tokens |> Map.keys() |> Enum.sort() |> Enum.map_join(", ", &"{{#{&1}}}")

        {:error,
         issue(
           path,
           pack_dir,
           line,
           "正文不能直接写死年份 #{Enum.join(literals, "、")}，请改用日期占位符：#{date_placeholders}"
         )}
    end
  end

  defp document_id(path, documents_dir) do
    path
    |> Path.relative_to(documents_dir)
    |> Path.rootname()
    |> String.split(["/", "\\"])
    |> Enum.join(".")
    |> then(&"doc.#{&1}")
  end

  # ==== pack-level validation ==== //

  defp validate_documents(pack) do
    Enum.flat_map(pack.documents, fn document ->
      check_claim_reference(document, pack) ++ check_claim_taboo(document, pack)
    end) ++ check_overlaps(pack)
  end

  defp check_claim_reference(%Document{claim: nil}, _pack), do: []

  defp check_claim_reference(%Document{claim: claim, id: id, source: source}, pack) do
    if Map.has_key?(pack.claims, claim) do
      []
    else
      ids = pack.claims |> Map.keys() |> Enum.sort()

      hint =
        case ids do
          [] -> "；归因卡表里没有可用条目，先修正 claims.tsv"
          ids -> "；现有：" <> Enum.join(ids, ", ") <> did_you_mean(claim, ids)
        end

      [issue_at(source, "档案卡 #{id} 引用了不存在的归因卡 #{inspect(claim)}" <> hint)]
    end
  end

  defp check_claim_taboo(%Document{claim: nil}, _pack), do: []

  defp check_claim_taboo(%Document{claim: claim, lens: lens, id: id, source: source}, pack) do
    case Map.fetch(pack.claims, claim) do
      :error ->
        # The missing claim is already reported by `check_claim_reference/2`.
        []

      {:ok, claim_row} ->
        cond do
          is_nil(lens) ->
            [
              issue_at(source, "档案卡 #{id} 引用了归因卡 #{claim}，但没有声明 lens")
            ]

          lens in claim_row.forbidden_for ->
            [
              issue_at(
                source,
                "#{lens} 口径不能引用 #{claim}（该卡的 forbidden_for 包含 #{lens}）"
              )
            ]

          lens not in claim_row.allowed_for ->
            [
              issue_at(
                source,
                "#{lens} 口径没有引用 #{claim} 的权限（allowed_for 只有 #{Enum.join(claim_row.allowed_for, ", ")}）"
              )
            ]

          true ->
            []
        end
    end
  end

  defp check_overlaps(pack) do
    documents = pack.documents

    for a <- documents,
        b <- documents,
        a.id < b.id,
        a.slot == b.slot,
        compatible?(a, b) do
      issue_at(
        a.source,
        "档案卡 #{a.id} 与 #{b.id} 的适用条件重叠：同一段历史会同时选中它们"
      )
    end
  end

  defp compatible?(%Document{} = a, %Document{} = b) do
    lists_intersect?(a.fates, b.fates) and
      lists_intersect?(a.relics, b.relics) and
      lists_intersect?(a.gazetteer_cases, b.gazetteer_cases)
  end

  defp lists_intersect?([], _other), do: true
  defp lists_intersect?(_this, []), do: true
  defp lists_intersect?(this, other), do: Enum.any?(this, &(&1 in other))

  defp validate_coverage(pack) do
    Enum.flat_map(@slots, fn {slot, spec} ->
      for combination <- combinations(spec.dims),
          count_matches(pack, slot, combination) == 0 do
        issue_at(
          %{file: "content/documents", line: 0},
          "#{describe_combination(combination)} 没有任何 #{slot} 文档适用"
        )
      end
    end)
  end

  defp combinations([]), do: [[]]

  defp combinations([:fate | rest]) do
    for fate <- Schema.fates(), tail <- combinations(rest), do: [{:fate, fate} | tail]
  end

  defp combinations([:relic | rest]) do
    for relic <- @relics, tail <- combinations(rest), do: [{:relic, relic} | tail]
  end

  defp combinations([:gazetteer_case | rest]) do
    for case_id <- @gazetteer_cases,
        tail <- combinations(rest),
        do: [{:gazetteer_case, case_id} | tail]
  end

  defp count_matches(pack, slot, combination) do
    criteria = Map.new(combination)
    Enum.count(pack.documents, &covers?(slot, criteria, &1))
  end

  # Coverage asks only about the dimensions a slot declares. A document pinned
  # to one precedent still covers its (fate, relic) cell: which precedence the
  # world actually selected decides at selection time whether it is used.
  defp covers?(slot, criteria, %Document{} = document) do
    document.slot == slot and
      membership(document.fates, Map.get(criteria, :fate)) and
      membership(document.relics, Map.get(criteria, :relic)) and
      case_covers?(document.gazetteer_cases, criteria)
  end

  defp case_covers?(_cases, criteria) when not is_map_key(criteria, :gazetteer_case), do: true
  defp case_covers?(cases, criteria), do: membership(cases, Map.get(criteria, :gazetteer_case))

  defp describe_combination(combination) do
    combination
    |> Enum.map(fn {key, value} -> "#{key}=#{value}" end)
    |> Enum.join(" + ")
    |> case do
      "" -> "全局"
      text -> text
    end
  end

  defp warnings(pack) do
    cited = pack.documents |> Enum.map(& &1.claim) |> MapSet.new()

    for {id, _claim} <- pack.claims, not MapSet.member?(cited, id) do
      %{file: "content/claims.tsv", line: 0, message: "归因卡 #{id} 没有任何文档引用"}
    end
  end

  # ==== selection ==== //

  defp select(pack, slot, criteria) do
    criteria = Map.new(criteria)
    matches = Enum.filter(pack.documents, &matches?(slot, criteria, &1))

    case matches do
      [document] ->
        {:ok, document}

      [] ->
        {:error, {:no_document, slot, criteria}}

      many ->
        {:error, {:ambiguous_documents, slot, criteria, Enum.map(many, & &1.id)}}
    end
  end

  defp matches?(slot, criteria, %Document{} = document) do
    document.slot == slot and
      membership(document.fates, Map.get(criteria, :fate)) and
      membership(document.relics, Map.get(criteria, :relic)) and
      membership(document.gazetteer_cases, Map.get(criteria, :gazetteer_case)) and
      lens_matches(document.lens, Map.get(criteria, :lens))
  end

  defp membership([], _value), do: true
  defp membership(_list, nil), do: false
  defp membership(list, value), do: value in list

  defp lens_matches(_document_lens, nil), do: true
  defp lens_matches(document_lens, lens), do: document_lens == lens

  # ==== assembly helpers ==== //

  # A notebook that declares which precedent it cites reports that precedent;
  # a wildcard notebook reports the one the history selected.
  defp corroborated_case(%Document{gazetteer_cases: [case_id]}, _fallback), do: case_id
  defp corroborated_case(_document, fallback), do: fallback

  defp bindings(names) do
    %{
      "place.up" => names.up,
      "place.pass" => names.pass,
      "place.down" => names.down
    }
    |> Map.merge(date_tokens())
    |> Map.to_list()
  end

  defp render_date({:reign_date, reign, anchor}),
    do: Timeline.reign_date(reign, Timeline.year(anchor))

  defp render_date({:short_year, anchor}), do: Timeline.short_year(Timeline.year(anchor))
  defp render_date({:year, anchor}), do: Integer.to_string(Timeline.year(anchor))

  # ==== parsing helpers ==== //

  defp table_rows(content) do
    content = content |> String.trim_leading("\uFEFF") |> String.replace("\r\n", "\n")

    content
    |> String.split("\n")
    |> Enum.with_index(1)
    |> Enum.reject(fn {line, _number} ->
      trimmed = String.trim(line)
      trimmed == "" or String.starts_with?(trimmed, "#")
    end)
    |> Enum.map(fn {line, number} -> {line, number} end)
  end

  defp split_row(row) do
    row |> String.split("\t") |> Enum.map(&String.trim/1)
  end

  defp split_list(cell) do
    cell
    |> String.split(",")
    |> Enum.map(&String.trim/1)
    |> Enum.reject(&(&1 == ""))
  end

  defp enum_value(nil, _allowed), do: :error

  defp enum_value(value, allowed) do
    case Enum.find(allowed, &(Atom.to_string(&1) == value)) do
      nil -> :error
      atom -> {:ok, atom}
    end
  end

  defp parse_optional_enum(nil, _allowed, _field, _path, _dir), do: {:ok, nil}

  defp parse_optional_enum(value, allowed, field, path, dir) do
    case enum_value(value, allowed) do
      {:ok, atom} ->
        {:ok, atom}

      :error ->
        {:error,
         issue(
           path,
           dir,
           2,
           "#{field} 的值 #{inspect(value)} 不合法；可用：#{Enum.join(allowed, ", ")}" <>
             did_you_mean(value, allowed)
         )}
    end
  end

  defp parse_enum_list(nil, _allowed, _field, _path, _dir), do: {:ok, []}

  defp parse_enum_list(value, allowed, field, path, dir) do
    value
    |> split_list()
    |> Enum.reduce_while({:ok, []}, fn item, {:ok, acc} ->
      case enum_value(item, allowed) do
        {:ok, atom} ->
          {:cont, {:ok, [atom | acc]}}

        :error ->
          {:halt,
           {:error,
            issue(
              path,
              dir,
              2,
              "#{field} 里的 #{inspect(item)} 不合法；可用：#{Enum.join(allowed, ", ")}" <>
                did_you_mean(item, allowed)
            )}}
      end
    end)
    |> case do
      {:ok, values} -> {:ok, Enum.reverse(values)}
      {:error, _issue} = error -> error
    end
  end

  defp did_you_mean(input, candidates, formatter \\ & &1)

  defp did_you_mean(input, candidates, formatter) when is_binary(input) and input != "" do
    downcased = String.downcase(input)

    candidates
    |> Enum.map(&to_string/1)
    |> Enum.map(fn candidate ->
      {candidate, String.jaro_distance(downcased, String.downcase(candidate))}
    end)
    |> Enum.max_by(&elem(&1, 1), fn -> {nil, 0.0} end)
    |> case do
      {best, score} when is_binary(best) and score >= 0.72 ->
        "（你是不是想写 #{inspect(formatter.(best))}？）"

      _ ->
        ""
    end
  end

  defp did_you_mean(_input, _candidates, _formatter), do: ""

  defp relative(path, dir), do: Path.relative_to(path, dir) |> String.replace("\\", "/")

  # Windows temp paths arrive with backslashes; Path.wildcard needs one
  # consistent separator, so normalise before walking the tree.
  defp normalize_dir(dir), do: dir |> Path.expand() |> Path.split() |> Path.join()

  defp issue(path, dir, line, message) do
    %{file: relative(path, dir), line: line, message: message}
  end

  defp issue_at(%{file: file, line: line}, message) do
    %{file: file, line: line, message: message}
  end
end
