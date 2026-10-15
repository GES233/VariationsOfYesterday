defmodule Logic.Core do
  # From
  # https://ges233.github.io/2026/1/Paper-view-HemannMuKanren2013/#%E9%A2%9D%E5%A4%96%E8%A1%A5%E5%85%85
  @moduledoc "附带 occurs-check、disequality (neq) 以及 fair-search 的 microKanren 实现。"

  defmodule Var do
    @moduledoc "逻辑变量。"
    @type t :: %__MODULE__{id: integer()}
    @type maybe_term :: t() | term()

    defstruct [:id]

    def var?(%__MODULE__{}), do: true
    def var?(_), do: false

    defimpl String.Chars do
      def to_string(%{id: id}), do: "?#{id}"
    end
  end

  defmodule State do
    @moduledoc """
    状态结构体。
    * `subst`: 替换表
    * `counter`: 用于生成新逻辑变量的计数器
    * `neq`: 不等约束表 [{u, v}, ...]
    """
    @type substitution :: %{Var.t() => term()}
    @type t :: %__MODULE__{
            subst: substitution(),
            counter: non_neg_integer(),
            neq: list({term(), term()})
          }
    defstruct subst: %{}, counter: 0, neq: []
  end

  # --- 核心操作 ---

  def walk(%Var{} = u, subst) do
    case Map.fetch(subst, u) do
      {:ok, v} -> walk(v, subst)
      :error -> u
    end
  end

  def walk(u, _), do: u

  @spec unify(Var.maybe_term(), Var.maybe_term(), State.t()) :: nil | State.t()
  def unify(u, v, %State{subst: s, neq: neq} = state) do
    case unify_terms(u, v, s) do
      nil ->
        nil

      new_subst ->
        case verify_neq(neq, new_subst, []) do
          nil -> nil
          valid_neq -> %{state | subst: new_subst, neq: valid_neq}
        end
    end
  end

  # 验证不等式约束列表：
  # - 若某对 {a, b} 在 new_subst 下已无法合一（nil），说明永不相等，该约束已满足可移除
  # - 若 {a, b} 合一后没有新增任何绑定（s_ext == subst），说明 a 与 b 已经恒等，违反 neq，返回 nil
  # - 否则保留该不等约束
  defp verify_neq([], _subst, acc), do: Enum.reverse(acc)

  defp verify_neq([{a, b} | rest], subst, acc) do
    case unify_terms(a, b, subst) do
      nil ->
        verify_neq(rest, subst, acc)

      ^subst ->
        nil

      _s_ext ->
        verify_neq(rest, subst, [{a, b} | acc])
    end
  end

  defp unify_terms(u, v, s) do
    u = walk(u, s)
    v = walk(v, s)

    cond do
      u == v ->
        s

      Var.var?(u) ->
        ext_s(u, v, s)

      Var.var?(v) ->
        ext_s(v, u, s)

      is_list(u) and is_list(v) ->
        unify_lists(u, v, s)

      is_tuple(u) and is_tuple(v) and tuple_size(u) == tuple_size(v) ->
        unify_terms(Tuple.to_list(u), Tuple.to_list(v), s)

      true ->
        nil
    end
  end

  defp unify_lists([], [], s), do: s

  defp unify_lists([u | us], [v | vs], s) do
    case unify_terms(u, v, s) do
      nil -> nil
      s_prime -> unify_terms(us, vs, s_prime)
    end
  end

  defp unify_lists(_, _, _), do: nil

  defp ext_s(u, v, s) do
    if occurs?(u, v, s) do
      nil
    else
      Map.put(s, u, v)
    end
  end

  defp occurs?(x, v, s) do
    v = walk(v, s)

    cond do
      x == v ->
        true

      Var.var?(v) ->
        false

      is_list(v) ->
        occurs_list?(x, v, s)

      is_tuple(v) ->
        v
        |> Tuple.to_list()
        |> Enum.any?(fn elem -> occurs?(x, elem, s) end)

      true ->
        false
    end
  end

  defp occurs_list?(_x, [], _s), do: false

  defp occurs_list?(x, [h | t], s) do
    occurs?(x, h, s) or occurs?(x, t, s)
  end

  # --- 搜索流 ---

  @type stream :: [] | {:mature, State.t(), stream()} | {:immature, (-> stream())}
  @type goal :: (State.t() -> stream())

  @spec mzero :: stream()
  def mzero, do: []

  @spec unit(State.t()) :: stream()
  def unit(%State{} = state), do: {:mature, state, []}

  @spec mplus(stream(), stream()) :: stream()
  def mplus([], s2), do: s2
  def mplus({:immature, f}, s2), do: {:immature, fn -> mplus(s2, f.()) end}
  def mplus({:mature, h, t}, s2), do: {:mature, h, mplus(t, s2)}

  @spec bind(stream(), goal()) :: stream()
  def bind([], _g), do: []
  def bind({:immature, f}, g), do: {:immature, fn -> bind(f.(), g) end}
  def bind({:mature, h, t}, g), do: mplus(g.(h), bind(t, g))

  # --- Goals (Constructors) ---

  @spec eq(Var.maybe_term(), Var.maybe_term()) :: goal()
  def eq(u, v) do
    fn %State{} = state ->
      case unify(u, v, state) do
        nil -> mzero()
        new_state -> unit(new_state)
      end
    end
  end

  @doc "不等约束 Goal (u =/= v)"
  @spec neq(Var.maybe_term(), Var.maybe_term()) :: goal()
  def neq(u, v) do
    fn %State{subst: s, neq: neq} = state ->
      case unify_terms(u, v, s) do
        nil ->
          unit(state)

        ^s ->
          mzero()

        _s_ext ->
          unit(%{state | neq: [{u, v} | neq]})
      end
    end
  end

  @spec conj(goal(), goal()) :: goal()
  def conj(g1, g2) do
    fn %State{} = state -> bind(g1.(state), g2) end
  end

  @spec disj(goal(), goal()) :: goal()
  def disj(g1, g2) do
    fn %State{} = state -> mplus(g1.(state), g2.(state)) end
  end

  @spec all(list(goal())) :: goal()
  def all([]), do: fn state -> unit(state) end
  def all([g]), do: g
  def all([g | gs]), do: conj(g, all(gs))

  @spec any(list(goal())) :: goal()
  def any([]), do: fn _state -> mzero() end
  def any([g]), do: g
  def any([g | gs]), do: disj(g, any(gs))

  @spec delay((-> goal())) :: goal()
  def delay(goal_fun) do
    fn %State{} = state -> {:immature, fn -> goal_fun.().(state) end} end
  end

  defp pull([]), do: []
  defp pull({:immature, f}), do: pull(f.())
  defp pull({:mature, _, _} = s), do: s

  def take(_stream, 0), do: []

  def take(stream, n) do
    case pull(stream) do
      [] -> []
      {:mature, h, t} -> [h | take(t, n - 1)]
    end
  end

  def take_all(stream) do
    case pull(stream) do
      [] -> []
      {:mature, h, t} -> [h | take_all(t)]
    end
  end

  def call_fresh(f) do
    fn %State{counter: c} = s ->
      v = %Var{id: c}
      goal = f.(v)
      new_state = %{s | counter: c + 1}
      goal.(new_state)
    end
  end

  @doc "一次引入多个逻辑变量: fresh(3, fn [a, b, c] -> ... end)"
  def fresh(0, f), do: f.([])

  def fresh(n, f) when n > 0 do
    call_fresh(fn v ->
      fresh(n - 1, fn vs ->
        f.([v | vs])
      end)
    end)
  end

  # --- 基础关系工具 ---

  def membero(x, list) do
    fresh(2, fn [h, t] ->
      conj(
        eq(list, [h | t]),
        disj(eq(x, h), delay(fn -> membero(x, t) end))
      )
    end)
  end

  @doc "受界属性查找：沿现有列表骨架查找 {k, v}，避免对未绑定尾部无限生成开列表"
  def lookupo(k, v, entity) do
    fn %State{subst: s} = state ->
      case walk(entity, s) do
        [] ->
          mzero()

        [h | t] ->
          disj(eq(h, {k, v}), lookupo(k, v, t)).(state)

        %Var{} = u ->
          # 若实体本身尚未绑定骨架，仅合一单元素骨架而非无限递归生成
          fresh(1, fn tail ->
            eq(u, [{k, v} | tail])
          end).(state)

        _ ->
          mzero()
      end
    end
  end

  @doc "开放属性匹配：只要 entity (Keyword list) 包含所需的所有 {k, v} 对即合一"
  def propso(_entity, []), do: fn state -> unit(state) end

  def propso(entity, [{k, v} | rest]) do
    conj(lookupo(k, v, entity), propso(entity, rest))
  end

  # --- Reify & Run ---

  def walk_star(v, s) do
    v = walk(v, s)

    cond do
      Var.var?(v) ->
        v

      is_list(v) ->
        walk_star_list(v, s)

      is_tuple(v) ->
        v |> Tuple.to_list() |> Enum.map(&walk_star(&1, s)) |> List.to_tuple()

      true ->
        v
    end
  end

  defp walk_star_list([], _s), do: []
  defp walk_star_list([h | t], s), do: [walk_star(h, s) | walk_star(t, s)]

  defp reify_s(v, s) do
    v = walk(v, s)

    cond do
      Var.var?(v) -> Map.put(s, v, :"_#{map_size(s)}")
      is_list(v) -> reify_s_list(v, s)
      is_tuple(v) -> v |> Tuple.to_list() |> Enum.reduce(s, &reify_s/2)
      true -> s
    end
  end

  defp reify_s_list([], s), do: s
  defp reify_s_list([h | t], s), do: reify_s(t, reify_s(h, s))

  def reify(var, %State{subst: s, neq: neq}) do
    walked = walk_star(var, s)
    reify_map = reify_s(walked, %{})
    reified_term = walk_star(walked, reify_map)

    # 将仍挂起的不等约束一并具象化（仅保留与当前查询结果中未绑定变量相关的约束）
    active_neq =
      neq
      |> Enum.map(fn {a, b} ->
        {walk_star(walk_star(a, s), reify_map), walk_star(walk_star(b, s), reify_map)}
      end)
      |> Enum.reject(fn {a, b} -> Var.var?(a) or Var.var?(b) end)
      |> Enum.uniq()

    if active_neq == [] do
      reified_term
    else
      [result: reified_term, where_neq: active_neq]
    end
  end

  def run(n, f) do
    call_fresh(f).(%State{})
    |> take(n)
    |> Enum.map(&reify(%Var{id: 0}, &1))
  end

  def run_all(f) do
    call_fresh(f).(%State{})
    |> take_all()
    |> Enum.map(&reify(%Var{id: 0}, &1))
  end
end

# ==============================================================================
# 《旧日变奏曲》天地盘 · 四课 · 三传 关系沙盘
# ==============================================================================
defmodule Voy.LiurenSandbox do
  import Logic.Core

  # ----------------------------------------------------------------------------
  # 1. 【地盘 Earth Plate】：山谷静态拓扑与聚落基线（不动之骨）
  # ----------------------------------------------------------------------------
  def dipan_edgeo(up, mid, down) do
    any([
      # 3号支沟：野猪箐（源头矿点） -> 断碑垭口（汇流要冲） -> 白水坝（下游聚落）
      all([eq(up, :boar_gully), eq(mid, :broken_stele_pass), eq(down, :baishui_flat)]),
      # 5号支沟：老君坪（原生林）   -> 响水涧（山涧）     -> 糯良寨（原生老寨）
      all([eq(up, :laojun_flat), eq(mid, :echo_brook), eq(down, :nuoliang_zhai)])
    ])
  end

  # 地盘聚落状态（是否经历过改制拆钟，决定 c0 是否健在）
  def settlement_stateo(place, org_type, c0_status) do
    any([
      # 白水坝：1979年由老寨改制为农场四队，铜钲被收缴，c0 归零
      all([eq(place, :baishui_flat), eq(org_type, :farm_brigade_04), eq(c0_status, :c0_collapsed)]),
      # 糯良寨：深山未改制老寨，保留寨门与岁祭铜钲，c0 充足
      all([eq(place, :nuoliang_zhai), eq(org_type, :ancestral_zhai), eq(c0_status, :c0_intact)])
    ])
  end

  # ----------------------------------------------------------------------------
  # 2. 【天盘 Heaven Plate】：动力学四相与慢应力加临（流转之气，对应 MandateReadout）
  #    这里不搞虚空占卜，而是描述「当下切面」的相变规律
  # ----------------------------------------------------------------------------
  def tianpan_transitiono(c0_status, regime, current_phase, pulse_type, next_state, lag_tau) do
    any([
      # 【宽容区吸收】：c0 充足且处于 Calm 时，即使有常规脉冲也被宽容区吸收（单次预兆后回落）
      all([
        eq(c0_status, :c0_intact),
        eq(regime, :Calm),
        eq(pulse_type, :heavy_kick),
        eq(next_state, :subthreshold_omen_then_calm),
        eq(lag_tau, :tau_immediate)
      ]),

      # 【易损期点火】：c0 坍塌 + 承压/振荡 + 40-60% 相对不应期 + 正向冲击 = 提前起峰（灾变发端！）
      all([
        eq(c0_status, :c0_collapsed),
        any([eq(regime, :Strained), eq(regime, :Oscillating)]),
        eq(current_phase, :RelativeRefractory),
        eq(pulse_type, :alpha_shock),
        eq(next_state, :premature_excitation_spike),
        eq(lag_tau, :tau_5)
      ]),

      # 【不应期推迟（黄金司钟窗）】：振荡期 + 20-30% 绝对不应期 + 低频金属 α 脉冲 = 推迟下一次尖峰！
      all([
        eq(regime, :Oscillating),
        eq(current_phase, :AbsoluteRefractory),
        eq(pulse_type, :alpha_metal_resonance),
        eq(next_state, :spike_delayed_by_8_6),
        eq(lag_tau, :tau_5)
      ]),

      # 【强行反向镇压致死（总指挥部湮灭线）】：高应力下强行打入反向巨幅镇压 = 冲穿截断阈值进入 Burnout！
      all([
        eq(regime, :Oscillating),
        eq(pulse_type, :OverwhelmingNegativeSuppression),
        eq(next_state, :attractor_burnout_annihilation),
        eq(lag_tau, :tau_10)
      ])
    ])
  end

  # ----------------------------------------------------------------------------
  # 3. 【四课 Four Lessons】：散落在山谷里的器物、动作与物候（待发用之机）
  #    严格绑定聚落组织形态 (org_type) 与基线状态 (c0_status)，防止跨聚落穿帮
  # ----------------------------------------------------------------------------
  def artifact_actiono(org_type, c0_status, actor_group, place_role, item_props) do
    any([
      # 林场采石班：在老寨上游使用常规黑火药（提供 :heavy_kick，验证天盘规则 1 宽容区吸收）
      all([
        eq(org_type, :ancestral_zhai),
        eq(c0_status, :c0_intact),
        eq(actor_group, :forestry_quarry_crew),
        eq(place_role, :upstream_source),
        eq(item_props, [
          name: "常规林区修路黑火药",
          pulse: :heavy_kick,
          trace_left: :charcoal_powder_residue
        ])
      ]),

      # 302地质勘探队：在改制农场上游沟谷使用2号岩石硝铵炸药（触发易损窗提前起峰）
      all([
        eq(org_type, :farm_brigade_04),
        eq(c0_status, :c0_collapsed),
        eq(actor_group, :survey_team_302),
        eq(place_role, :upstream_source),
        eq(item_props, [
          name: "2号岩石硝铵炸药点炮",
          pulse: :alpha_shock,
          trace_left: :blasting_cap_wire
        ])
      ]),

      # 原生老寨祭司：仅在保留了铜钲的老寨（c0_intact）汇流垭口敲击镇山铜钲
      all([
        eq(org_type, :ancestral_zhai),
        eq(c0_status, :c0_intact),
        eq(actor_group, :old_zhai_priest),
        eq(place_role, :midstream_pass),
        eq(item_props, [
          name: "岁祭镇山铜钲三叩",
          pulse: :alpha_metal_resonance,
          trace_left: :verdigris_mallet_mark
        ])
      ]),

      # 退化新寨（四队留守职工）：在铜钲被收缴（c0_collapsed）的绝境下，用东方红气缸替代司钟
      all([
        eq(org_type, :farm_brigade_04),
        eq(c0_status, :c0_collapsed),
        eq(actor_group, :assimilated_brigade_04),
        eq(place_role, :midstream_pass),
        eq(item_props, [
          name: "红绸包裹的东方红-54气缸体敲击礼",
          pulse: :alpha_metal_resonance,
          trace_left: :red_cloth_and_grease
        ])
      ]),

      # 总指挥部直属注浆处：在源头强行打入超高压反向水泥注浆（触发天盘规则 4 Burnout 湮灭）
      all([
        eq(org_type, :farm_brigade_04),
        eq(c0_status, :c0_collapsed),
        eq(actor_group, :hq_grouting_command),
        eq(place_role, :upstream_source),
        eq(item_props, [
          name: "超高压反向水泥封井注浆",
          pulse: :OverwhelmingNegativeSuppression,
          trace_left: :calcified_grout_seal
        ])
      ])
    ])
  end

  # 快变量四相对应的地表物候与土法仪器读数（对应设计草案 §2.2）
  def phase_phenomenono(phase, natural_sign, instrument_sign) do
    any([
      all([
        eq(phase, :Excitation),
        eq(natural_sign, "地鸣落石、涧水翻涌成浑汤"),
        eq(instrument_sign, "验山垂架铅锤剧烈撞击木框")
      ]),
      all([
        eq(phase, :AbsoluteRefractory),
        eq(natural_sign, "林中虫鸣骤停、谷雾贴地倒吸（死寂回吸）"),
        eq(instrument_sign, "改装舌簧监听匣陷入绝对静默、挂钟摆锤紧绷")
      ]),
      all([
        eq(phase, :RelativeRefractory),
        eq(natural_sign, "水生鱼鳞高频驻波、惊鸟乱飞（蓄势躁动）"),
        eq(instrument_sign, "验山簧片高频蜂鸣、验裂纸条微颤")
      ]),
      all([
        eq(phase, :Resting),
        eq(natural_sign, "山涧清澈、薄雾缓行"),
        eq(instrument_sign, "垂架铅锤居中不动")
      ])
    ])
  end

  # 根据 action_place_role 将动作发生地锚定到具体的地盘节点（上游源头 vs 中游垭口）
  def action_siteo(up, mid, role, action_site) do
    any([
      all([eq(role, :upstream_source), eq(action_site, up)]),
      all([eq(role, :midstream_pass), eq(action_site, mid)])
    ])
  end

  # ----------------------------------------------------------------------------
  # 4. 【三传 Three Transmissions】：因 Goal（所求之事）而发用！
  #    初传（起手触发） -> 中传（平流与滞后移易） -> 末传（下游显化归宿）
  # ----------------------------------------------------------------------------
  def sanchuano(chuchuan, zhongchuan, mochuan) do
    fresh(16, fn [
      up, mid, down, org, c0,
      regime, phase, actor, role, action_site, item,
      pulse, next_state, lag, nat_sign, inst_sign
    ] ->
      all([
        # 天地盘相合
        dipan_edgeo(up, mid, down),
        settlement_stateo(down, org, c0),
        phase_phenomenono(phase, nat_sign, inst_sign),

        # 四课取用（与聚落组织、c0 状态及具体地理节点严格绑定）
        artifact_actiono(org, c0, actor, role, item),
        action_siteo(up, mid, role, action_site),
        propso(item, [pulse: pulse]),

        # 发用推演
        tianpan_transitiono(c0, regime, phase, pulse, next_state, lag),

        # 初传：何时、何地、何物候下、由谁动了什么手脚
        eq(chuchuan, [
          upstream: up,
          pass: mid,
          action_site: action_site,
          action_role: role,
          phase: phase,
          natural_sign: nat_sign,
          instrument_sign: inst_sign,
          actor: actor,
          action_item: item
        ]),

        # 中传：地势平流与突触延迟 τ、基线 c0 状态
        eq(zhongchuan, [
          from: up,
          via: mid,
          to: down,
          c0_baseline: c0,
          synapse_lag: lag,
          regime: regime
        ]),

        # 末传：下游聚落最终承受的动力学相变结果
        eq(mochuan, [
          settlement: down,
          org: org,
          outcome: next_state
        ])
      ])
    end)
  end

  # ----------------------------------------------------------------------------
  # 5. 【多世界观认知透镜 Lenses】：
  #    构建共享的「归因解释池」，再用 neq（意识形态禁忌）迫使不同透镜筛出各自的说法
  # ----------------------------------------------------------------------------
  def attribution_candidateo(regime, cause_tag, explanation_text) do
    any([
      # 解释候选 1：官方替罪羊（秋汛自然岩移）
      all([
        eq(regime, :Strained),
        eq(cause_tag, :autumn_rain_natural_erosion),
        eq(explanation_text, "十月连日秋雨导致野猪箐常规岩层疲劳滑坡（系自然损耗，与排险爆破无关）")
      ]),
      # 解释候选 2：技术真相 A（拆钟 c0 归零 + 承压区易损窗点炮初犯击穿）
      all([
        eq(regime, :Strained),
        eq(cause_tag, :c0_collapsed_and_vulnerable_blasting),
        eq(explanation_text, "此前白水坝恢复变慢（临界慢化 Strained），79年收走铜钲后阻尼归零（c0=0）；302队点炮正撞在簧片蜂鸣段（易损窗），5刻后推过失稳点")
      ]),
      # 解释候选 3：老寨民俗解释（拆钟断香火 + 换气口动火药惊动山神）
      all([
        eq(regime, :Strained),
        eq(cause_tag, :mountain_god_breath_disturbed),
        eq(explanation_text, "涧水起鱼鳞碎纹、雀鸟惊飞乃山脉倒换气！四队拆了寨门铜钲断香火，勘探队又在换气口动火药致山神翻身")
      ]),
      # 解释候选 4：技术真相 B（已在自持振荡期二次误点火）
      all([
        eq(regime, :Oscillating),
        eq(cause_tag, :secondary_ignition_in_oscillation),
        eq(explanation_text, "谷地明明已陷入周期性自激振荡（Oscillating），302队在回潮易损窗追加爆破，把原本40刻后的尖峰直接提前引爆")
      ])
    ])
  end

  # 各透镜的认知禁忌（真正用 neq 裁剪候选池！）
  def lens_tabooo(lens, cause_tag) do
    any([
      all([
        eq(lens, :OfficialHQ),
        # 官样通报严禁承认：拆钟改制/时机失误、二次振荡误点火、或山神发怒
        neq(cause_tag, :c0_collapsed_and_vulnerable_blasting),
        neq(cause_tag, :secondary_ignition_in_oscillation),
        neq(cause_tag, :mountain_god_breath_disturbed)
      ]),
      all([
        eq(lens, :TechInner),
        # 技术员内参鄙视官方秋汛借口，也不采信山神鬼怪之说
        neq(cause_tag, :autumn_rain_natural_erosion),
        neq(cause_tag, :mountain_god_breath_disturbed)
      ]),
      all([
        eq(lens, :OldZhai),
        # 老寨幸存者不认官方秋汛借口，也不用现代动力学方程术语
        neq(cause_tag, :autumn_rain_natural_erosion),
        neq(cause_tag, :c0_collapsed_and_vulnerable_blasting),
        neq(cause_tag, :secondary_ignition_in_oscillation)
      ])
    ])
  end

  def lens_projectiono(lens, chuchuan, zhongchuan, mochuan, narrative_record) do
    fresh(7, fn [phase, outcome, c0, regime, item, item_name, cause_tag] ->
      fresh(1, fn [explanation] ->
        all([
          propso(chuchuan, [phase: phase, action_item: item]),
          propso(item, [name: item_name]),
          propso(zhongchuan, [c0_baseline: c0, regime: regime]),
          propso(mochuan, [outcome: outcome]),
          eq(outcome, :premature_excitation_spike),
          eq(phase, :RelativeRefractory),
          eq(c0, :c0_collapsed),

          # 先挂载当前透镜的 neq 禁忌，再投入共享候选池合一！
          membero(lens, [:OfficialHQ, :TechInner, :OldZhai]),
          lens_tabooo(lens, cause_tag),
          attribution_candidateo(regime, cause_tag, explanation),

          eq(narrative_record, [
            lens: lens,
            regime_context: regime,
            trigger_artifact: item_name,
            adopted_cause_tag: cause_tag,
            record_text: explanation
          ])
        ])
      end)
    end)
  end
end

# ==============================================================================
# 运行沙盘测试！
# ==============================================================================

IO.puts("==================================================================")
IO.puts("【推演一：溯因断案（已知末传灾变，反推初传与中传）】")
IO.puts("所求之事（Goal）：白水坝（:baishui_flat）突然爆发了提前尖峰（:premature_excitation_spike），")
IO.puts("请问在「天地盘」与「四课」中，是谁、在什么物候下、干了什么引发的？")
IO.puts("==================================================================")

results_1 =
  Logic.Core.run_all(fn q ->
    Logic.Core.fresh(3, fn [chu, zhong, mo] ->
      Logic.Core.all([
        Voy.LiurenSandbox.sanchuano(chu, zhong, mo),
        Logic.Core.propso(mo, [
          settlement: :baishui_flat,
          outcome: :premature_excitation_spike
        ]),
        Logic.Core.eq(q, [初传_发端: chu, 中传_移易: zhong, 末传_归宿: mo])
      ])
    end)
  end)

IO.inspect(results_1, pretty: true, width: 100)

IO.puts("\n==================================================================")
IO.puts("【推演二：一案三表（同一场三传灾变，在三套世界观透镜下自动衍生不冲突的档案）】")
IO.puts("==================================================================")

results_2 =
  Logic.Core.run_all(fn q ->
    Logic.Core.fresh(4, fn [chu, zhong, mo, lens] ->
      Logic.Core.all([
        Voy.LiurenSandbox.sanchuano(chu, zhong, mo),
        Logic.Core.propso(mo, [
          settlement: :baishui_flat,
          outcome: :premature_excitation_spike
        ]),
        Voy.LiurenSandbox.lens_projectiono(lens, chu, zhong, mo, q)
      ])
    end)
  end)

IO.inspect(results_2, pretty: true, width: 100)

IO.puts("\n==================================================================")
IO.puts("【推演三：绝境求生（退化新寨的邪典仪式合成 — 不预设行动者！）】")
IO.puts("所求之事（Goal）：仅指定白水坝（:baishui_flat）要得末传「推迟尖峰（:spike_delayed_by_8_6）」。")
IO.puts("不预设是谁干的（?actor 留空），由 Kanren 根据白水坝 c0 已坍塌的事实，自动排除老寨铜钲，")
IO.puts("唯一推演出：只能由四队留守职工在断碑垭口、卡死死寂回吸窗、敲击东方红气缸体！")
IO.puts("==================================================================")

results_3 =
  Logic.Core.run_all(fn q ->
    Logic.Core.fresh(9, fn [chu, zhong, mo, actor, site, phase, nat_sign, inst_sign, item] ->
      Logic.Core.all([
        Voy.LiurenSandbox.sanchuano(chu, zhong, mo),
        Logic.Core.propso(chu, [
          actor: actor,
          action_site: site,
          phase: phase,
          natural_sign: nat_sign,
          instrument_sign: inst_sign,
          action_item: item
        ]),
        Logic.Core.propso(mo, [
          settlement: :baishui_flat,
          outcome: :spike_delayed_by_8_6
        ]),
        Logic.Core.eq(q, [
          推导出的唯一执行者: actor,
          仪式地点: site,
          必须卡准的相位: phase,
          眼见物候口诀: nat_sign,
          土法仪器读数: inst_sign,
          替代法器与仪轨: item
        ])
      ])
    end)
  end)

IO.inspect(results_3, pretty: true, width: 100)

# ==============================================================================
# 第二部分：四纪发展史与玩家入场切面编译器 (Voy.ChronicleCompiler)
# ------------------------------------------------------------------------------
# 解决痛点：不用手写海量分支剧本！
# 输入：从任意种子（含外部地形模组）D8 高度图提取出的「山谷拓扑特征」
# 推演：第一纪（建寨） -> 第二纪（改制与工程进山） -> 第三纪（激化与三分结局）
# 输出：第四纪（玩家入场时的 Mandate 初始场、结构 NBT 落位、多视角自洽档案包）
# ==============================================================================
defmodule Voy.ChronicleCompiler do
  import Logic.Core

  # ----------------------------------------------------------------------------
  # 【第一纪：老寨肇建 (Era I - Ancestral Settlement)】
  # 规则：依地势建寨，坝子/台地聚居，必在咽喉隘口立镇山之器以维系基线安抚 c0
  # ----------------------------------------------------------------------------
  def era1_ancestralo(topo, era1) do
    fresh(5, fn [branch_id, up_type, pass_type, down_type, guardian_relic] ->
      all([
        propso(topo, [
          branch_id: branch_id,
          up_terrain: up_type,
          pass_terrain: pass_type,
          down_terrain: down_type
        ]),
        # 下游聚落选址必须是开阔冲积坝或半山台地
        any([eq(down_type, :alluvial_flat), eq(down_type, :mid_terrace)]),
        # 咽喉隘口根据地貌立不同的镇山器物（均提供 c0_intact）
        any([
          all([
            eq(pass_type, :canyon_pass),
            eq(guardian_relic, :ancestral_bronze_zheng)
          ]),
          all([
            eq(pass_type, :karst_gorge),
            eq(guardian_relic, :carved_diversion_stele)
          ])
        ]),
        eq(era1, [
          branch_id: branch_id,
          settlement_form: :ancestral_zhai,
          guardian_relic: guardian_relic,
          initial_c0: :c0_intact,
          initial_regime: :Calm
        ])
      ])
    end)
  end

  # ----------------------------------------------------------------------------
  # 【第二纪：改制与工程进山 (Era II - Reorganization & Survey, 1960s-1970s)】
  # 规则：
  # 1. 下游地貌决定交通可达性，进而决定「寨改村/农场」是否发生（拆毁镇山器 -> c0 坍塌）；
  # 2. 源头地貌与地质构造共同决定进驻哪种工程分队，埋下不同性质的扰动源。
  # ----------------------------------------------------------------------------
  def era2_reorgo(topo, era1, era2) do
    fresh(10, fn [
      down_type,
      access,
      geology,
      up_type,
      relic,
      org_after,
      c0_after,
      relic_fate,
      eng_team,
      disturbance_mode
    ] ->
      all([
        propso(topo, [
          down_terrain: down_type,
          accessibility: access,
          up_geology: geology,
          up_terrain: up_type
        ]),
        propso(era1, [guardian_relic: relic]),

        # 聚落改制分支：开阔冲积坝修通机耕道被改制为农场分队；半山台地绝壁隔绝得以幸存
        any([
          all([
            eq(down_type, :alluvial_flat),
            eq(access, :tractor_road_accessible),
            eq(org_after, :state_farm_brigade),
            eq(relic_fate, {:requisitioned_or_dismantled, relic}),
            eq(c0_after, :c0_collapsed)
          ]),
          all([
            eq(down_type, :mid_terrace),
            eq(access, :cliff_isolated),
            eq(org_after, :unreformed_old_zhai),
            eq(relic_fate, {:preserved_in_situ, relic}),
            eq(c0_after, :c0_intact)
          ])
        ]),

        # 源头工程进驻分支：按源头地貌与断裂带性质派驻不同工程单位
        any([
          # 陡峭裂隙矿脉 -> 302 勘探队进行浅层密集点炮（周期性冲击易损窗）
          all([
            eq(up_type, :steep_fissure_headwater),
            eq(geology, :active_fault_vein),
            eq(eng_team, :survey_blasting_team_302),
            eq(disturbance_mode, :periodic_shallow_blasting)
          ]),
          # 喀斯特落水洞/暗河 -> 总指挥部直属高压注浆处（企图用巨量反向压力闷死地脉）
          all([
            eq(up_type, :karst_sinkhole_headwater),
            eq(geology, :deep_stress_cavity),
            eq(eng_team, :hq_high_pressure_grouting_corps),
            eq(disturbance_mode, :overwhelming_reverse_suppression)
          ]),
          # 陡峭次生裂隙林区 -> 仅设常规水文气象哨（轻微扰动）
          all([
            eq(up_type, :steep_fissure_headwater),
            eq(geology, :minor_fracture),
            eq(eng_team, :forestry_hydro_post),
            eq(disturbance_mode, :low_level_logging_shock)
          ])
        ]),

        eq(era2, [
          org_after_reorg: org_after,
          relic_fate: relic_fate,
          c0_status: c0_after,
          stationed_team: eng_team,
          disturbance_mode: disturbance_mode
        ])
      ])
    end)
  end

  # ----------------------------------------------------------------------------
  # 【第三纪：激化与三分结局 (Era III - Catastrophe & Fates, early 1980s)】
  # 规则：严格对齐设计草案 §1.1 动力学方程与 §4.5 主体下落（同化 / 湮灭 / 飞升 / 幸存对照）
  # ----------------------------------------------------------------------------
  def era3_catastropheo(_topo, _era1, era2, era3) do
    fresh(7, fn [
      org,
      c0,
      dist_mode,
      fate_category,
      resulting_regime,
      target_phase_rule,
      emergent_practice
    ] ->
      all([
        propso(era2, [
          org_after_reorg: org,
          c0_status: c0,
          disturbance_mode: dist_mode
        ]),

        any([
          # 结局分支 1：【同化 -> 退化新寨】
          # 条件：c0 坍塌 + 勘探队密集爆破击穿宽容区 -> 陷入自持振荡 (Oscillating)
          # 撤场断粮后，留守农场职工用拖拉机气缸与交接班制度在 AbsoluteRefractory 合成替代仪式
          all([
            eq(org, :state_farm_brigade),
            eq(c0, :c0_collapsed),
            eq(dist_mode, :periodic_shallow_blasting),
            eq(fate_category, :Fate_Assimilated_NewZhai),
            eq(resulting_regime, :Oscillating),
            eq(target_phase_rule, :must_strike_at_AbsoluteRefractory),
            eq(emergent_practice, [
              costume: "褪色劳保服与红袖章",
              instrument: "红绸包裹的东方红-54铸铁气缸体",
              liturgy: "虫鸣骤停死寂时，按《交接班制度》列队唱名三声并重锤击缸"
            ])
          ]),

          # 结局分支 2：【湮灭 -> 烧毁态死寂区】
          # 条件：c0 坍塌 + 总指挥部直属高压反向注浆镇压 -> 应力冲穿截断阈值进入 Burnout 吸引子
          all([
            eq(c0, :c0_collapsed),
            eq(dist_mode, :overwhelming_reverse_suppression),
            eq(fate_category, :Fate_Annihilated_BurnoutZone),
            eq(resulting_regime, :Burnout),
            eq(target_phase_rule, :phase_frozen_in_white_silence),
            eq(emergent_practice, [
              phenomenon: "灰白钙化地表、无声无息、钟摆焊死在最大偏角",
              remnant: "被水泥彻底封死的注浆井口与全员失踪名册"
            ])
          ]),

          # 结局分支 3：【飞升 -> 空间畸变放逐点】
          # 条件：虽有古寨 c0 但源头为深层应力腔且遭遇高压强行谐振 -> 核心人员在尖峰时刻自我放逐
          all([
            eq(c0, :c0_intact),
            eq(dist_mode, :overwhelming_reverse_suppression),
            eq(fate_category, :Fate_Ascended_SpatialRift),
            eq(resulting_regime, :Strained),
            eq(target_phase_rule, :triggered_at_Excitation_zenith),
            eq(emergent_practice, [
              phenomenon: "游荡的搬运方块黑影（末影畸变）、指南针乱转的残破观测掩体",
              remnant: "写满高频相位坐标与『去往对岸』字样的磁带"
            ])
          ]),

          # 结局分支 4：【幸存对照 -> 宽容区吸收的老寨】
          # 条件：c0 完好 + 常规林区轻微扰动 -> 方程宽容区吸收脉冲（单次预兆后迅速回落 Calm）
          all([
            eq(org, :unreformed_old_zhai),
            eq(c0, :c0_intact),
            eq(dist_mode, :low_level_logging_shock),
            eq(fate_category, :Fate_Ancestral_ControlGroup),
            eq(resulting_regime, :Calm),
            eq(target_phase_rule, :absorbed_in_Resting_basin),
            eq(emergent_practice, [
              phenomenon: "涧水偶有微澜但很快平息，老寨岁祭如常",
              remnant: "完好运转的镇山古器与世代相传的听山口诀"
            ])
          ])
        ]),

        eq(era3, [
          fate: fate_category,
          final_regime: resulting_regime,
          phase_rule: target_phase_rule,
          manifestation: emergent_practice
        ])
      ])
    end)
  end

  # ----------------------------------------------------------------------------
  # 【第四纪：玩家入场切面与「两套账本」编译器 (Era IV - Present-Day Slice)】
  # 将前三纪推演出的历史，直接编译为：
  # 1. 地形节点上的结构落位 (Structures)
  # 2. Mandate 引擎各格初始状态 (Mandate Initial State)
  # 3. 散落在箱子里的多透镜档案（带 neq 防串戏约束）
  # ----------------------------------------------------------------------------
  def compile_branch_sliceo(topo, slice) do
    fresh(6, fn [branch_id, era1, era2, era3, structures, archives] ->
      all([
        propso(topo, [branch_id: branch_id]),
        era1_ancestralo(topo, era1),
        era2_reorgo(topo, era1, era2),
        era3_catastropheo(topo, era1, era2, era3),
        compile_structureso(topo, era1, era2, era3, structures),
        compile_archiveso(topo, era1, era2, era3, archives),
        eq(slice, [
          支沟代号: branch_id,
          地理骨相: topo,
          第一纪_古寨肇建: era1,
          第二纪_改制与进山: era2,
          第三纪_激化与结局: era3,
          第四纪_落地结构与遗迹: structures,
          第四纪_生成档案包: archives
        ])
      ])
    end)
  end

  defp compile_structureso(topo, era1, era2, era3, structures) do
    fresh(6, fn [up_name, pass_name, down_name, relic, fate, team] ->
      all([
        propso(topo, [up_name: up_name, pass_name: pass_name, down_name: down_name]),
        propso(era1, [guardian_relic: relic]),
        propso(era2, [stationed_team: team]),
        propso(era3, [fate: fate]),
        any([
          all([
            eq(fate, :Fate_Assimilated_NewZhai),
            eq(structures, [
              upstream_site: {up_name, "302队废弃爆破钻探台 (常驻脉冲源)"},
              pass_site: {pass_name, "断碑垭口·悬挂东方红气缸体的简易木架 + 验山垂架"},
              downstream_site: {down_name, "农场四队砖混宿舍区 (variations-of-yesterday:farm_dorm_unit_*)"}
            ])
          ]),
          all([
            eq(fate, :Fate_Annihilated_BurnoutZone),
            eq(structures, [
              upstream_site: {up_name, "水泥封死的超高压注浆井群 (灰白死寂核心)"},
              pass_site: {pass_name, "坍塌的石雕分水堰与焊死的验山垂架"},
              downstream_site: {down_name, "空无一人的钙化农场废墟 (Burnout 绝对静默区)"}
            ])
          ]),
          all([
            eq(fate, :Fate_Ascended_SpatialRift),
            eq(relic, :ancestral_bronze_zheng),
            eq(structures, [
              upstream_site: {up_name, "深渊谐振掩体与黑曜石畸变裂隙 (末影人游荡点)"},
              pass_site: {pass_name, "尚存岁祭镇山铜钲的险峻峡口"},
              downstream_site: {down_name, "半山绝壁孤寨 (偶受空间裂隙微扰)"}
            ])
          ]),
          all([
            eq(fate, :Fate_Ascended_SpatialRift),
            eq(relic, :carved_diversion_stele),
            eq(structures, [
              upstream_site: {up_name, "深渊谐振掩体与黑曜石畸变裂隙 (末影人游荡点)"},
              pass_site: {pass_name, "尚存石雕分水古碑的溶洞峡口"},
              downstream_site: {down_name, "半山绝壁孤寨 (偶受空间裂隙微扰)"}
            ])
          ]),
          all([
            eq(fate, :Fate_Ancestral_ControlGroup),
            eq(structures, [
              upstream_site: {up_name, "废弃林场水文观测哨"},
              pass_site: {pass_name, "古寨镇山铜钲亭 (c0 维持源完好)"},
              downstream_site: {down_name, "原生老寨聚落 (安居乐业，提供听山口诀对照)"}
            ])
          ])
        ])
      ])
    end)
  end

  # 同一结局下的多源解释池（含内部真相与官方替罪羊），供 neq 禁忌主动筛选
  defp branch_cause_candidateo(fate, cause_tag, summary_text) do
    any([
      # 同化线候选：真相 vs 官方替罪羊
      all([
        eq(fate, :Fate_Assimilated_NewZhai),
        eq(cause_tag, :c0_removed_and_vulnerable_window_blasting),
        eq(summary_text, "79年收缴铜钲后 c0 归零，302队点炮正撞上簧片蜂鸣段（易损窗）致自持振荡")
      ]),
      all([
        eq(fate, :Fate_Assimilated_NewZhai),
        eq(cause_tag, :seasonal_rain_mudslide),
        eq(summary_text, "归咎于连日秋雨导致常规岩移滑坡，隐瞒拆钟与点炮")
      ]),
      # 湮灭线候选：真相 vs 官方替罪羊
      all([
        eq(fate, :Fate_Annihilated_BurnoutZone),
        eq(cause_tag, :overwhelming_suppression_attractor_burnout),
        eq(summary_text, "总指挥部强行打入反向巨量阻尼，应力冲穿 s_max 截断阈值，整条沟进入死寂吸引子")
      ]),
      all([
        eq(fate, :Fate_Annihilated_BurnoutZone),
        eq(cause_tag, :planned_relocation_and_mine_closure),
        eq(summary_text, "宣称矿区资源枯竭、全员已整建制平稳搬迁")
      ]),
      # 飞升线候选：真相 vs 官方替罪羊
      all([
        eq(fate, :Fate_Ascended_SpatialRift),
        eq(cause_tag, :excitation_zenith_spatial_exile),
        eq(summary_text, "他们在起峰极值（Excitation）打开了谐振腔，核心组全变成了搬石头的黑影")
      ]),
      all([
        eq(fate, :Fate_Ascended_SpatialRift),
        eq(cause_tag, :equipment_retrieval_completed),
        eq(summary_text, "深孔观测站设备回收完毕，人员调离备案")
      ]),
      # 幸存古寨候选：真相 vs 官方口径
      all([
        eq(fate, :Fate_Ancestral_ControlGroup),
        eq(cause_tag, :intact_c0_basin_absorption),
        eq(summary_text, "铜钲镇垭口，山涧小震不过三息自平；莫在鱼鳞水纹时动土，要在万籁俱寂时叩钟")
      ]),
      all([
        eq(fate, :Fate_Ancestral_ControlGroup),
        eq(cause_tag, :backward_folk_custom_pending_reform),
        eq(summary_text, "山高路远暂缓改制，旧式鸣钟习俗待后续引导")
      ])
    ])
  end

  # 官方汇报严禁采信任何涉及工程失误、c0 坍塌、Burnout 或空间畸变的真实原因
  defp official_tabooo(cause_tag) do
    all([
      neq(cause_tag, :c0_removed_and_vulnerable_window_blasting),
      neq(cause_tag, :overwhelming_suppression_attractor_burnout),
      neq(cause_tag, :excitation_zenith_spatial_exile),
      neq(cause_tag, :intact_c0_basin_absorption)
    ])
  end

  defp compile_archiveso(topo, _era1, _era2, era3, archives) do
    fresh(7, fn [up_name, down_name, fate, official_claim, official_text, inner_truth, inner_text] ->
      all([
        propso(topo, [up_name: up_name, down_name: down_name]),
        propso(era3, [fate: fate]),

        # 1. 官方通报从候选池中取用，但受 official_tabooo 的 neq 约束裁剪，只能落入替罪羊解释
        official_tabooo(official_claim),
        branch_cause_candidateo(fate, official_claim, official_text),

        # 2. 内参/民间深层记录同样从候选池取用，但强制 neq(inner_truth, official_claim)
        neq(inner_truth, official_claim),
        branch_cause_candidateo(fate, inner_truth, inner_text),

        eq(archives, [
          表层_官样通报: [
            target_settlement: down_name,
            upstream_site: up_name,
            claimed_cause: official_claim,
            summary: official_text
          ],
          深层_内参或口述真相: [
            true_source: up_name,
            settlement: down_name,
            true_cause: inner_truth,
            note: inner_text
          ]
        ])
      ])
    end)
  end
end

IO.puts("\n==================================================================")
IO.puts("【推演四：从地形骨相自动生成「四纪发展史」与玩家入场世界切面】")
IO.puts("场景：D8 算法从当前世界种子（或外部地形模组）的高度图中抠出了 3 条支沟。")
IO.puts("设计师只需声明地形特征，Kanren 自动推演出每一条沟的四纪历史、建筑落位与三层档案！")
IO.puts("==================================================================")

# 模拟从高度图 D8 提取出的三条支沟拓扑特征
extracted_terrain_branches = [
  # 支沟 1：陡峭裂隙源头 + 峡谷隘口 + 通了机耕道的开阔坝子
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
  ],
  # 支沟 2：喀斯特落水洞源头 + 溶洞峡口 + 通了公路的矿区平坝
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
  ],
  # 支沟 3：普通次生裂隙源头 + 峡谷隘口 + 绝壁隔绝的半山台地
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
  ]
]

compiled_valley_history =
  Enum.flat_map(extracted_terrain_branches, fn branch_topo ->
    Logic.Core.run_all(fn q ->
      Voy.ChronicleCompiler.compile_branch_sliceo(branch_topo, q)
    end)
  end)

IO.inspect(compiled_valley_history, pretty: true, width: 100)

IO.puts("\n==================================================================")
IO.puts("【推演五：从结局倒推地形与历史（Goal-Driven Worldgen 选址）】")
IO.puts("场景：设计师希望在玩家探索的下一个区域生成一个「飞升（空间畸变放逐）」遗迹。")
IO.puts("直接向 Kanren 提问：什么样的地形骨相（?topo）与前三纪历史，才能演生出 :Fate_Ascended_SpatialRift？")
IO.puts("==================================================================")

reverse_worldgen_query =
  Logic.Core.run_all(fn q ->
    Logic.Core.fresh(7, fn [required_topo, slice, up_t, pass_t, down_t, geo, acc] ->
      Logic.Core.all([
        # 只绑定地名占位，5项地形特征全部留作未知逻辑变量让 Kanren 反推！
        Logic.Core.eq(required_topo, [
          branch_id: :mystery_branch,
          up_name: "未知源头",
          pass_name: "未知隘口",
          down_name: "未知聚落",
          up_terrain: up_t,
          pass_terrain: pass_t,
          down_terrain: down_t,
          up_geology: geo,
          accessibility: acc
        ]),
        Voy.ChronicleCompiler.compile_branch_sliceo(required_topo, slice),
        Logic.Core.propso(slice, [
          第三纪_激化与结局: [
            fate: :Fate_Ascended_SpatialRift,
            final_regime: :Strained,
            phase_rule: :triggered_at_Excitation_zenith,
            manifestation: [
              phenomenon: "游荡的搬运方块黑影（末影畸变）、指南针乱转的残破观测掩体",
              remnant: "写满高频相位坐标与『去往对岸』字样的磁带"
            ]
          ]
        ]),
        Logic.Core.eq(q, slice)
      ])
    end)
  end)

IO.inspect(reverse_worldgen_query, pretty: true, width: 100)
