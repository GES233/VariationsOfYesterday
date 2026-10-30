defmodule Voy.World.Content do
  @moduledoc """
  Authoring pack for the five-hop local-evidence narrative.

  `Pathway` owns relations and bridge terms; this module owns prose. Every
  `(relic, fate)` pathway selects five main cards and two decoys from Markdown
  files, with no global summary and no duplicate templates in Elixir.
  """

  alias Voy.World.{Pathway, Schema, Witness}

  @pack_dir Path.expand("../../../content", __DIR__)
  @relics [:ancestral_bronze_zheng, :carved_diversion_stele]
  @roles [:main, :decoy]
  @c0_states [:c0_collapsed, :c0_intact]
  @decoy_classes [:material_mismatch, :destination_dead_end]
  @front_matter_keys ~w(hop role relic fate c0_status decoy_class title)
  @placeholders ~w(
    place.up place.pass place.down batch_id vessel_name vessel_code
    onset_period onset_percent onset_ticks settled_period settled_percent
    settled_ticks settled_delay safe_tick safe_phase decoy_a_batch
    decoy_a_vessel_name decoy_a_vessel_code decoy_b_batch
  )

  defmodule Document do
    @moduledoc "One local-evidence card and its applicability conditions."
    defstruct [:id, :hop, :role, :relic, :fate, :c0_status, :decoy_class, :title, :body, :source]
  end

  defmodule Pack do
    @moduledoc "A loaded five-hop content pack."
    defstruct documents: [], dir: nil
  end

  @doc "Directory the pack is read from (override with `VOY_CONTENT_DIR`)."
  def pack_dir do
    case System.get_env("VOY_CONTENT_DIR") do
      nil -> @pack_dir
      dir -> Path.expand(dir)
    end
  end

  def relics, do: @relics
  def placeholders, do: @placeholders

  @doc "Load and validate every Markdown card under `dir/documents`."
  def load(dir \\ pack_dir()) do
    dir = Path.expand(dir)
    root = Path.join(dir, "documents")

    documents =
      root
      |> Path.join("**/*.md")
      |> Path.wildcard()
      |> Enum.sort()
      |> Enum.map(&parse_document(&1, root))

    issues = Enum.flat_map(documents, & &1.errors)

    case issues do
      [] ->
        pack = %Pack{documents: Enum.map(documents, & &1.document), dir: dir}
        coverage_issues = validate_coverage(pack)

        case coverage_issues do
          [] -> {:ok, pack}
          errors -> {:error, errors}
        end

      errors ->
        {:error, errors}
    end
  end

  def load!(dir \\ pack_dir()) do
    case load(dir) do
      {:ok, pack} -> pack
      {:error, issues} -> raise ArgumentError, Enum.map_join(issues, "\n", &format_issue/1)
    end
  end

  def default_pack, do: load!()

  @doc "Select exactly one card for a pathway edge."
  def card_for(criteria, pack \\ nil) do
    pack = pack || default_pack()
    criteria = Map.new(criteria)

    matches =
      Enum.filter(pack.documents, fn document ->
        document.role == criteria[:role] and
          document.relic == criteria[:relic] and
          (document.role == :decoy or document.hop == criteria[:hop]) and
          document.fate == criteria[:fate] and
          document.c0_status == criteria[:c0_status] and
          document.decoy_class == criteria[:decoy_class]
      end)

    case matches do
      [document] -> {:ok, document}
      [] -> {:error, {:no_document, criteria}}
      many -> {:error, {:ambiguous_documents, criteria, Enum.map(many, & &1.id)}}
    end
  end

  @doc "Render one body, refusing any unresolved placeholder."
  def render(body, bindings) when is_binary(body) do
    rendered =
      Enum.reduce(bindings, body, fn {name, value}, acc ->
        String.replace(acc, "{{#{name}}}", to_string(value))
      end)

    case Regex.run(~r/\{\{([a-z0-9_.]+)\}\}/, rendered) do
      nil -> rendered
      [_, name] -> raise ArgumentError, "unresolved placeholder {{#{name}}}"
    end
  end

  @doc "Run author-facing checks without raising."
  def lint(dir \\ pack_dir()) do
    case load(dir) do
      {:ok, _pack} -> %{errors: [], warnings: []}
      {:error, errors} -> %{errors: errors, warnings: []}
    end
  end

  def format_issue(%{file: file, line: line, message: message}), do: "#{file}:#{line}: #{message}"

  @doc "Starter cards for authors."
  def template(kind) do
    case to_string(kind) do
      "hop" <> hop when hop in ~w(1 2 3 4 5) ->
        fate = if hop in ["4", "5"], do: "fate: Fate_Assimilated_NewZhai\n", else: ""
        c0 = if hop == "2", do: "c0_status: c0_collapsed\n", else: ""

        {:ok,
         "---\nhop: #{hop}\nrole: main\nrelic: ancestral_bronze_zheng\n#{fate}#{c0}title: 新单据\n---\n\n{{place.up}}……\n"}

      "decoy" ->
        {:ok,
         "---\nrole: decoy\nrelic: ancestral_bronze_zheng\ndecoy_class: material_mismatch\ntitle: 干扰单据\n---\n\n{{decoy_a_batch}}……\n"}

      other ->
        allowed = ~w(hop1 hop2 hop3 hop4 hop5 decoy)
        {:error, "未知卡片类型 #{inspect(other)}；可用：#{Enum.join(allowed, " / ")}"}
    end
  end

  # ==== parsing and validation ==== //

  defp parse_document(path, root) do
    relative = Path.relative_to(path, root)
    id = String.replace_suffix(relative, ".md", "")
    source = %{file: Path.join("content/documents", relative), line: 1}
    text = path |> File.read!() |> String.trim_leading("\uFEFF") |> String.replace("\r\n", "\n")

    with {:ok, fields, body} <- split_document(text, source),
         :ok <- check_fields(fields, source),
         {:ok, document} <- build_document(id, fields, body, source),
         :ok <- check_body(document) do
      %{document: document, errors: []}
    else
      {:error, issues} -> %{document: nil, errors: List.wrap(issues)}
    end
  end

  defp split_document(text, source) do
    case Regex.run(~r/\A---\n(.*?)\n---\n(.*)\z/s, text) do
      [_, header, body] ->
        fields =
          header
          |> String.split("\n")
          |> Enum.reject(&(String.trim(&1) == "" or String.starts_with?(String.trim(&1), "#")))
          |> Map.new(fn line ->
            case String.split(line, ":", parts: 2) do
              [key, value] -> {String.trim(key), String.trim(value)}
              _ -> {line, ""}
            end
          end)

        {:ok, fields, String.trim(body)}

      _ ->
        {:error, issue(source, "档案卡需要以 --- 包围的平铺头部和正文")}
    end
  end

  defp check_fields(fields, source) do
    unknown = Map.keys(fields) -- @front_matter_keys

    case unknown do
      [] ->
        :ok

      keys ->
        {:error,
         issue(
           source,
           "未知字段 #{Enum.map_join(keys, "、", &inspect/1)}；可用字段：#{Enum.join(@front_matter_keys, " / ")}"
         )}
    end
  end

  defp build_document(id, f, body, source) do
    with {:ok, role} <- enum_field(f, "role", @roles, true, source),
         {:ok, relic} <- enum_field(f, "relic", @relics, true, source),
         {:ok, fate} <- enum_field(f, "fate", Schema.fates(), false, source),
         {:ok, c0} <- enum_field(f, "c0_status", @c0_states, false, source),
         {:ok, decoy} <- enum_field(f, "decoy_class", @decoy_classes, false, source),
         {:ok, hop} <- hop_field(f, role, source),
         :ok <- check_conditions(role, hop, fate, c0, decoy, source),
         :ok <- required_text(f, "title", source),
         :ok <- required_text(%{"body" => body}, "body", source) do
      {:ok,
       %Document{
         id: id,
         hop: hop,
         role: role,
         relic: relic,
         fate: fate,
         c0_status: c0,
         decoy_class: decoy,
         title: f["title"],
         body: body,
         source: source
       }}
    end
  end

  defp enum_field(fields, key, allowed, required, source) do
    value = Map.get(fields, key)

    cond do
      value in [nil, ""] and required ->
        {:error, issue(source, "缺少必填字段：#{key}")}

      value in [nil, ""] ->
        {:ok, nil}

      true ->
        atom = String.to_atom(value)

        if atom in allowed,
          do: {:ok, atom},
          else: {:error, issue(source, "#{key} 里的 #{inspect(value)} 不合法")}
    end
  end

  defp hop_field(fields, :main, source) do
    case Integer.parse(Map.get(fields, "hop", "")) do
      {hop, ""} when hop in 1..5 -> {:ok, hop}
      _ -> {:error, issue(source, "主链卡 hop 必须是 1..5")}
    end
  end

  defp hop_field(_fields, :decoy, _source), do: {:ok, nil}

  defp check_conditions(:main, 2, nil, c0, nil, _source) when c0 != nil, do: :ok

  defp check_conditions(:main, 2, nil, nil, nil, source),
    do: {:error, issue(source, "Hop 2 缺少必填字段：c0_status")}

  defp check_conditions(:main, 2, _fate, _c0, _decoy, source),
    do: {:error, issue(source, "Hop 2 只需要 c0_status，不应写 fate/decoy_class")}

  defp check_conditions(:main, hop, fate, nil, nil, _source) when hop in [4, 5] and fate != nil,
    do: :ok

  defp check_conditions(:main, hop, _fate, _c0, _decoy, source) when hop in [4, 5],
    do: {:error, issue(source, "Hop #{hop} 只需要 fate，且 fate 必填")}

  defp check_conditions(:main, hop, nil, nil, nil, _source) when hop in [1, 3], do: :ok

  defp check_conditions(:main, _hop, _fate, _c0, _decoy, source),
    do: {:error, issue(source, "Hop 1/3 不应写 fate/c0_status/decoy_class")}

  defp check_conditions(:decoy, nil, nil, nil, decoy, _source) when decoy != nil, do: :ok

  defp check_conditions(:decoy, _hop, _fate, _c0, _decoy, source),
    do: {:error, issue(source, "干扰卡只需要 relic 与 decoy_class")}

  defp required_text(fields, key, source) do
    if String.trim(Map.get(fields, key, "")) == "",
      do: {:error, issue(source, "缺少必填字段：#{key}")},
      else: :ok
  end

  defp check_body(document) do
    unknown =
      Regex.scan(~r/\{\{([a-z0-9_.]+)\}\}/, document.body, capture: :all_but_first)
      |> List.flatten()
      |> Enum.reject(&(&1 in @placeholders))

    hardcoded_year = Regex.match?(~r/(?<!\d)(\d{4}|\d{2})年/, document.body)

    cond do
      unknown != [] ->
        {:error, issue(document.source, "无法解析的占位符：#{Enum.map_join(unknown, "、", &"{{#{&1}}}")}")}

      hardcoded_year ->
        {:error, issue(document.source, "正文不能直接写死年份；年代锚点由结构层管理")}

      true ->
        :ok
    end
  end

  defp validate_coverage(pack) do
    duplicate_ids =
      pack.documents
      |> Enum.group_by(& &1.id)
      |> Enum.filter(fn {_, docs} -> length(docs) > 1 end)
      |> Enum.map(fn {id, _} ->
        issue(%{file: "content/documents", line: 0}, "档案 id 重复：#{id}")
      end)

    edge_issues =
      for relic <- Pathway.relics(), fate <- Schema.fates(), reduce: [] do
        acc ->
          {:ok, %{chain: chain, decoys: decoys}} = Pathway.compile_chain(relic, fate)

          Enum.reduce(chain ++ decoys, acc, fn edge, errors ->
            role = if edge.hop in [1, 2, 3, 4, 5] and edge in chain, do: :main, else: :decoy

            criteria = %{
              role: role,
              hop: edge.hop,
              relic: relic,
              fate: if(role == :main and edge.hop in [4, 5], do: fate),
              c0_status: if(role == :main and edge.hop == 2, do: edge.attrs[:c0_status]),
              decoy_class: edge.attrs[:decoy_class]
            }

            case card_for(criteria, pack) do
              {:ok, _} ->
                errors

              {:error, reason} ->
                [
                  issue(%{file: "content/documents", line: 0}, "五跳卡片选择失败：#{inspect(reason)}")
                  | errors
                ]
            end
          end)
      end

    duplicate_ids ++ Enum.reverse(edge_issues)
  end

  defp issue(source, message), do: %{file: source.file, line: source.line, message: message}

  # ==== preview ==== //

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

  def preview_presets, do: Enum.map(@preview_presets, &elem(&1, 0))

  def preview(target \\ "all", opts \\ []) do
    target = to_string(target)

    case Keyword.get(opts, :seed, 101) do
      seed when is_integer(seed) ->
        canonical = Map.get(@preview_aliases, target, target)

        cond do
          canonical == "all" ->
            {:ok,
             Enum.map_join(@preview_presets, "\n\n", fn {name, topo} ->
               format_preset(name, topo, seed)
             end)}

          preset = List.keyfind(@preview_presets, canonical, 0) ->
            {:ok, format_preset(canonical, elem(preset, 1), seed)}

          true ->
            {:error,
             "未知预览节点 #{inspect(target)}；可用：#{Enum.join(preview_presets() ++ ["all"], " / ")}"}
        end

      seed ->
        {:error, "seed 必须是整数，实际是 #{inspect(seed)}"}
    end
  end

  defp format_preset(name, topo, seed) do
    {:ok, [slice]} = Voy.World.compile_branch(topo)
    relic = slice["era1"]["guardian_relic"] |> String.to_existing_atom()
    fate = slice["era3"]["fate"] |> String.to_existing_atom()
    names = %{up: topo[:up_name], pass: topo[:pass_name], down: topo[:down_name]}
    dossier = Witness.render_dossier(seed, topo[:branch_id], relic, fate, names)

    cards =
      Enum.map_join(dossier.documents, "\n\n", fn document ->
        label =
          if document.role == :main,
            do: "真链 Hop #{document.hop}",
            else: "干扰 #{document.decoy_class}"

        "· [#{label}｜#{document.lens}] #{document.title}\n  #{document.body}"
      end)

    """
    ==================================================================
    【节点档案预览：#{name}（#{topo[:down_name]}）】
    支沟：#{topo[:up_name]} → #{topo[:pass_name]} → #{topo[:down_name]}
    结局：#{slice["era3"]["fate"]}（慢变量分区：#{slice["era3"]["final_regime"]}）
    古器：#{slice["era1"]["guardian_relic"]}
    【五跳微链卷宗（seed=#{seed}，含2份同车间干扰单据）】
    勘定格：第#{dossier.binding.safe_tick}格（φ≈#{:erlang.float_to_binary(dossier.binding.safe_phase * 1.0, decimals: 2)}，实测推迟 #{dossier.binding.safe_delay} 刻）

    #{cards}
    ==================================================================\
    """
  end
end
