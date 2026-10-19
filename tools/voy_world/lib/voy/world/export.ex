defmodule Voy.World.Export do
  @moduledoc """
  The stable wire format between this prototype and the JVM port.

  A bundle is everything the game side needs for one or more nodes and
  nothing else: the schema version, the rule set, the numeric stock that
  produced it (`Voy.World.Profile`), and normalized node records. The
  payload is deliberately *not* whatever the relation happens to return:
  keys are fixed here, structures become `site`/`description` maps, and
  tagged tuples become `tag`/`value` maps, so a Scala reader can be
  written once.

  Determinism: no timestamps and no wall clock are stamped in, so the
  same inputs produce byte-identical JSON and the golden cases stay
  usable. `validate/1` also refuses any payload that would not survive a
  JSON round-trip, which keeps accidental atoms and tuples out of the
  contract.
  """

  use Voy.Kanren

  alias Voy.World.{Chronicle, Node, Profile, Schema}

  @schema_version 1

  @node_keys [
    "node_id",
    "branch_id",
    "rule_set",
    "sites",
    "names",
    "topography",
    "history",
    "structures",
    "archives"
  ]

  @site_keys ["up", "pass", "down"]
  @structure_keys ["upstream", "pass", "downstream"]
  @structure_sources [
    {"upstream", :upstream_site},
    {"pass", :pass_site},
    {"downstream", :downstream_site}
  ]
  @era_keys ["era1", "era2a", "era2b", "era3"]
  @archive_keys ["gazetteer", "official_report", "tech_note", "council_document"]

  @doc "Wire-format version stamped into every bundle."
  @spec schema_version() :: pos_integer()
  def schema_version, do: @schema_version

  @doc """
  Compile one named node and wrap it in a bundle.

  Options: `:features` (partial topography attributes; omitted ones are
  solved for), `:profile` (numeric stock, defaults to
  `Voy.World.Profile.default/0`).
  """
  @spec export_node(Keyword.t() | map(), Keyword.t()) :: {:ok, map()} | {:error, term()}
  def export_node(spec_fields, opts \\ []) do
    profile = Keyword.get(opts, :profile, Profile.default())
    features = Keyword.get(opts, :features, [])

    with {:ok, profile} <- ensure_profile(profile),
         {:ok, records} <- compile_node(spec_fields, features),
         {:ok, records} <- normalize_records(records) do
      build_bundle(records, profile)
    end
  end

  @doc """
  Wrap an already-solved candidate (for example from
  `Voy.World.reverse_node/1`) without re-solving it.
  """
  @spec export_candidate(Keyword.t() | map(), map() | Keyword.t(), Keyword.t()) ::
          {:ok, map()} | {:error, term()}
  def export_candidate(spec_fields, candidate, opts \\ []) do
    profile = Keyword.get(opts, :profile, Profile.default())

    with {:ok, profile} <- ensure_profile(profile),
         {:ok, record} <- materialize_node(spec_fields, candidate),
         {:ok, records} <- normalize_records([record]) do
      build_bundle(records, profile)
    end
  end

  @doc """
  Compile one branch topography and wrap it in a bundle.

  The branch API carries no display specification, so `node_id` defaults
  to the branch id and site ids are derived from it unless the topography
  already carries `up_site` / `pass_site` / `down_site`.
  """
  @spec export_branch(Keyword.t() | map(), Keyword.t()) :: {:ok, map()} | {:error, term()}
  def export_branch(topo_fields, opts \\ []) do
    profile = Keyword.get(opts, :profile, Profile.default())

    with {:ok, profile} <- ensure_profile(profile),
         {:ok, topo} <- topology(topo_fields) do
      slices =
        run_all(fn query -> Chronicle.compile_branch_sliceo(topo, query) end)
        |> terms()

      case slices do
        [] -> {:error, classify_branch(topo)}
        slices -> slices |> Enum.map(&branch_record(topo, &1, opts)) |> build_bundle(profile)
      end
    end
  end

  @doc "Validate a bundle, including the JSON round-trip gate."
  @spec validate(term()) :: :ok | {:error, term()}
  def validate(%{"schema_version" => version} = bundle) when is_map(bundle) do
    with :ok <- check_version(version),
         :ok <- check_rule_set(bundle),
         {:ok, nodes} <- fetch_nodes(bundle),
         :ok <- check_generator(bundle),
         :ok <- check_profile(bundle),
         :ok <- check_nodes(nodes, bundle),
         :ok <- check_json_safe(bundle) do
      :ok
    end
  end

  def validate(%{"schema_version" => version}),
    do: {:error, {:invalid_bundle, {:schema_version, version, :expected_integer}}}

  def validate(bundle), do: {:error, {:invalid_bundle, {:missing_schema_version, bundle}}}

  @doc "Validate, then encode as canonical JSON (stable key order, no whitespace)."
  @spec to_json(map()) :: {:ok, String.t()} | {:error, term()}
  def to_json(bundle) do
    with :ok <- validate(bundle) do
      {:ok, JSON.encode!(bundle)}
    end
  end

  @doc "Decode JSON, then validate the resulting bundle."
  @spec from_json(String.t()) :: {:ok, map()} | {:error, term()}
  def from_json(json) when is_binary(json) do
    case JSON.decode(json) do
      {:ok, bundle} ->
        with :ok <- validate(bundle), do: {:ok, bundle}

      {:error, reason} ->
        {:error, {:invalid_json, reason}}
    end
  end

  def from_json(other), do: {:error, {:not_json, other}}

  @doc "Profile recorded in a bundle, rebuilt as a validated struct."
  @spec profile(map()) :: {:ok, Profile.t()} | {:error, term()}
  def profile(%{"profile" => profile}) when is_map(profile), do: Profile.from_map(profile)
  def profile(bundle), do: {:error, {:missing_profile, bundle}}

  # ==== bundle assembly ==== //

  defp build_bundle(records, %Profile{} = profile) do
    rule_sets = records |> Enum.map(& &1["rule_set"]) |> Enum.uniq()

    case rule_sets do
      [rule_set] ->
        {:ok,
         %{
           "schema_version" => @schema_version,
           "generator" => generator(),
           "rule_set" => rule_set,
           "profile" => Profile.describe(profile),
           "nodes" => records
         }}

      [] ->
        {:error, :empty_bundle}

      mixed ->
        {:error, {:mixed_rule_sets, mixed}}
    end
  end

  defp generator do
    %{
      "app" => "voy_world",
      "version" => app_version()
    }
  end

  defp app_version do
    case Application.spec(:voy_world, :vsn) do
      nil -> "unknown"
      version -> to_string(version)
    end
  end

  defp ensure_profile(%Profile{} = profile) do
    case Profile.validate(profile) do
      :ok -> {:ok, profile}
      {:error, _reason} = error -> error
    end
  end

  defp ensure_profile(other), do: {:error, {:invalid_profile, other}}

  # Feature tables are raised on, not returned as errors; the export API
  # reports them instead so a caller cannot crash a world-generation pass.
  defp compile_node(spec_fields, features) do
    Node.compile(spec_fields, features)
  rescue
    error in [KeyError, ArgumentError] -> {:error, {:invalid_features, Exception.message(error)}}
  end

  defp materialize_node(spec_fields, candidate) do
    Node.materialize(spec_fields, candidate)
  rescue
    error in [KeyError, ArgumentError] -> {:error, {:invalid_features, Exception.message(error)}}
  end

  defp topology(topo_fields) do
    {:ok, Schema.topology(topo_fields)}
  rescue
    error in [KeyError, ArgumentError] ->
      {:error, {:invalid_topography, Exception.message(error)}}
  end

  defp classify_branch(topo) do
    era2b? =
      run(1, fn query -> Chronicle.history_through_era2bo(topo, query) end) != []

    if era2b?, do: :unresolved_history, else: :unsupported_topography
  end

  # ==== record normalization ==== //

  defp normalize_records(records) when is_list(records) do
    {:ok, Enum.map(records, &node_record/1)}
  end

  defp node_record(record) do
    topo = Keyword.fetch!(record, :topography)
    branch_id = record |> Keyword.get(:branch_id) |> to_string()
    node_id = record |> Keyword.get(:node_id) |> to_string()

    normalize(%{
      "node_id" => node_id,
      "branch_id" => branch_id,
      "rule_set" => to_string(Keyword.fetch!(record, :rule_set)),
      "sites" => sites(topo, branch_id),
      "names" => Keyword.fetch!(record, :names),
      "topography" => topo,
      "history" => history(record),
      "structures" => Keyword.fetch!(record, :structures),
      "archives" => Keyword.fetch!(record, :archives)
    })
  end

  defp branch_record(topo, slice, opts) do
    branch_id = to_string(topo[:branch_id])
    node_id = Keyword.get(opts, :node_id, branch_id) |> to_string()

    normalize(%{
      "node_id" => node_id,
      "branch_id" => branch_id,
      "rule_set" => Keyword.get(opts, :rule_set, :default_branch_v1) |> to_string(),
      "sites" => sites(topo, branch_id),
      "names" => names(topo, node_id),
      "topography" => topo,
      "history" =>
        Map.new(@era_keys, fn era ->
          {era, Keyword.fetch!(slice, String.to_existing_atom(era))}
        end),
      "structures" => Keyword.fetch!(slice, :structures),
      "archives" => Keyword.fetch!(slice, :archives)
    })
  end

  defp history(record) do
    Enum.into(@era_keys, %{}, fn era ->
      {era, Keyword.fetch!(record, String.to_existing_atom(era))}
    end)
  end

  defp sites(topo, branch_id) do
    Map.new(@site_keys, fn site ->
      short = String.to_existing_atom(site)

      {site, topo |> Keyword.get(:"#{short}_site", "#{branch_id}/#{site}") |> to_string()}
    end)
  end

  defp names(topo, node_id) do
    %{
      "node" => node_id,
      "up" => to_string(topo[:up_name]),
      "pass" => to_string(topo[:pass_name]),
      "down" => to_string(topo[:down_name])
    }
  end

  # Structures are a pair of (display name, description) per site: an
  # explicit mapping keeps the wire format readable instead of turning
  # them into ambiguous two-element arrays.
  defp normalize(%{"structures" => structures} = record) do
    record
    |> Map.put("structures", normalize_structures(structures))
    |> normalize_term()
  end

  defp normalize(record), do: normalize_term(record)

  defp normalize_structures(structures) do
    Map.new(@structure_sources, fn {key, source} ->
      {key, site_entry(Keyword.fetch!(structures, source))}
    end)
  end

  defp site_entry({name, description}) do
    %{"site" => to_string(name), "description" => to_string(description)}
  end

  defp site_entry(other), do: %{"site" => nil, "description" => to_string(other)}

  # Wire-format normalization: keyword lists become maps, arity-2 tagged
  # tuples become tag/value maps, atoms become strings. Anything that is
  # already JSON survives unchanged.
  defp normalize_term(value) when is_map(value) do
    Map.new(value, fn {key, item} -> {key_to_string(key), normalize_term(item)} end)
  end

  defp normalize_term([]), do: []

  defp normalize_term(value) when is_list(value) do
    if keyword_list?(value) do
      Map.new(value, fn {key, item} -> {key_to_string(key), normalize_term(item)} end)
    else
      Enum.map(value, &normalize_term/1)
    end
  end

  defp normalize_term({tag, value}) when is_atom(tag) do
    %{"tag" => Atom.to_string(tag), "value" => normalize_term(value)}
  end

  defp normalize_term({first, second}) do
    [normalize_term(first), normalize_term(second)]
  end

  defp normalize_term(value) when is_tuple(value) do
    value |> Tuple.to_list() |> normalize_term()
  end

  defp normalize_term(value) when is_atom(value), do: Atom.to_string(value)
  defp normalize_term(value), do: value

  defp keyword_list?(list) do
    Enum.all?(list, fn
      {key, _value} when is_atom(key) -> true
      _other -> false
    end)
  end

  defp key_to_string(key) when is_atom(key), do: Atom.to_string(key)
  defp key_to_string(key), do: key

  # ==== validation ==== //

  defp check_version(version) when version == @schema_version, do: :ok

  defp check_version(version),
    do: {:error, {:invalid_bundle, {:schema_version, version, @schema_version}}}

  defp check_rule_set(%{"rule_set" => rule_set}) when is_binary(rule_set) and rule_set != "",
    do: :ok

  defp check_rule_set(%{"rule_set" => rule_set}),
    do: {:error, {:invalid_bundle, {:rule_set, rule_set}}}

  defp check_rule_set(bundle),
    do: {:error, {:invalid_bundle, {:missing_field, :rule_set, bundle}}}

  defp fetch_nodes(%{"nodes" => nodes}) when is_list(nodes) and nodes != [], do: {:ok, nodes}
  defp fetch_nodes(%{"nodes" => nodes}), do: {:error, {:invalid_bundle, {:nodes, nodes}}}
  defp fetch_nodes(bundle), do: {:error, {:invalid_bundle, {:missing_field, :nodes, bundle}}}

  defp check_generator(%{"generator" => %{"app" => app, "version" => version}})
       when is_binary(app) and is_binary(version),
       do: :ok

  defp check_generator(%{"generator" => generator}),
    do: {:error, {:invalid_bundle, {:generator, generator}}}

  defp check_generator(bundle),
    do: {:error, {:invalid_bundle, {:missing_field, :generator, bundle}}}

  defp check_profile(%{"profile" => profile}) when is_map(profile) do
    case Profile.from_map(profile) do
      {:ok, _profile} -> :ok
      {:error, reason} -> {:error, {:invalid_bundle, {:profile, reason}}}
    end
  end

  defp check_profile(bundle), do: {:error, {:invalid_bundle, {:missing_field, :profile, bundle}}}

  defp check_nodes(nodes, %{"rule_set" => rule_set}) do
    with :ok <- check_unique_node_ids(nodes) do
      Enum.reduce_while(nodes, :ok, fn node, :ok ->
        case check_node(node, rule_set) do
          :ok -> {:cont, :ok}
          {:error, _reason} = error -> {:halt, error}
        end
      end)
    end
  end

  defp check_unique_node_ids(nodes) do
    ids = Enum.map(nodes, fn node -> Map.get(node, "node_id") end)

    case ids -- Enum.uniq(ids) do
      [] -> :ok
      duplicates -> {:error, {:invalid_bundle, {:duplicate_node_ids, duplicates}}}
    end
  end

  defp check_node(node, rule_set) when is_map(node) do
    with :ok <- check_node_keys(node),
         :ok <- check_node_identity(node, rule_set),
         :ok <- check_sites(node),
         :ok <- check_names(node),
         :ok <- check_structures(node),
         :ok <- check_history(node),
         :ok <- check_archives(node) do
      :ok
    end
  end

  defp check_node(node, _rule_set), do: {:error, {:invalid_bundle, {:node, node}}}

  defp check_node_keys(node) do
    missing = Enum.reject(@node_keys, &Map.has_key?(node, &1))

    case missing do
      [] -> :ok
      keys -> {:error, {:invalid_bundle, {:missing_node_keys, keys}}}
    end
  end

  defp check_node_identity(node, rule_set) do
    with :ok <- check_string(node, "node_id"),
         :ok <- check_string(node, "branch_id"),
         :ok <- check_string(node, "rule_set") do
      case node["rule_set"] do
        ^rule_set -> :ok
        other -> {:error, {:invalid_bundle, {:node_rule_set, node["node_id"], other, rule_set}}}
      end
    end
  end

  defp check_sites(%{"sites" => sites}) when is_map(sites) do
    Enum.reduce_while(@site_keys, :ok, fn key, :ok ->
      case Map.fetch(sites, key) do
        {:ok, value} when is_binary(value) and value != "" -> {:cont, :ok}
        other -> {:halt, {:error, {:invalid_bundle, {:site, key, other}}}}
      end
    end)
  end

  defp check_sites(node), do: {:error, {:invalid_bundle, {:sites, node["sites"]}}}

  defp check_names(%{"names" => names}) when is_map(names) do
    missing = Enum.reject(["node", "up", "pass", "down"], &Map.has_key?(names, &1))

    if missing == [],
      do: :ok,
      else: {:error, {:invalid_bundle, {:missing_names, missing}}}
  end

  defp check_names(node), do: {:error, {:invalid_bundle, {:names, node["names"]}}}

  defp check_structures(%{"structures" => structures}) when is_map(structures) do
    Enum.reduce_while(@structure_keys, :ok, fn key, :ok ->
      case Map.fetch(structures, key) do
        {:ok, %{"site" => site, "description" => description}}
        when is_binary(site) and is_binary(description) ->
          {:cont, :ok}

        other ->
          {:halt, {:error, {:invalid_bundle, {:structure, key, other}}}}
      end
    end)
  end

  defp check_structures(node),
    do: {:error, {:invalid_bundle, {:structures, node["structures"]}}}

  defp check_history(%{"history" => history}) when is_map(history) do
    missing = Enum.reject(@era_keys, &Map.has_key?(history, &1))

    if missing == [], do: :ok, else: {:error, {:invalid_bundle, {:missing_eras, missing}}}
  end

  defp check_history(node), do: {:error, {:invalid_bundle, {:history, node["history"]}}}

  defp check_archives(%{"archives" => archives}) when is_map(archives) do
    missing = Enum.reject(@archive_keys, &Map.has_key?(archives, &1))

    if missing == [],
      do: :ok,
      else: {:error, {:invalid_bundle, {:missing_archives, missing}}}
  end

  defp check_archives(node), do: {:error, {:invalid_bundle, {:archives, node["archives"]}}}

  defp check_string(map, key) do
    case Map.fetch(map, key) do
      {:ok, value} when is_binary(value) and value != "" -> :ok
      other -> {:error, {:invalid_bundle, {:field, key, other}}}
    end
  end

  # A bundle that cannot survive a JSON round-trip is not a wire format.
  defp check_json_safe(bundle) do
    if JSON.decode!(JSON.encode!(bundle)) == bundle do
      :ok
    else
      {:error, {:invalid_bundle, :not_json_safe}}
    end
  rescue
    error -> {:error, {:invalid_bundle, {:not_json_safe, Exception.message(error)}}}
  end
end
