defmodule Logic.Core do
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

  defp occurs_list?(_x, _non_list_tail, _s), do: false

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

  @doc "开放属性匹配：只要 entity (Keyword list) 包含所需的所有 {k, v} 对即合一"
  def propso(_entity, []), do: fn state -> unit(state) end

  def propso(entity, [{k, v} | rest]) do
    conj(membero({k, v}, entity), propso(entity, rest))
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

  def reify(var, %State{subst: s}) do
    walked = walk_star(var, s)
    walk_star(walked, reify_s(walked, %{}))
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
  # ----------------------------------------------------------------------------
  def artifact_actiono(actor_group, place_role, item_props) do
    any([
      # 302地质勘探队：在源头沟谷使用2号岩石硝铵炸药
      all([
        eq(actor_group, :survey_team_302),
        eq(place_role, :upstream_source),
        eq(item_props, [
          name: "2号岩石硝铵炸药点炮",
          pulse: :alpha_shock,
          trace_left: :blasting_cap_wire
        ])
      ]),

      # 原生老寨祭司：在汇流垭口或寨门敲击镇山铜钲
      all([
        eq(actor_group, :old_zhai_priest),
        eq(place_role, :midstream_pass),
        eq(item_props, [
          name: "岁祭镇山铜钲三叩",
          pulse: :alpha_metal_resonance,
          trace_left: :verdigris_mallet_mark
        ])
      ]),

      # 退化新寨（四队留守职工）：用大锤砸报废东方红拖拉机气缸 + 念交接班条例
      all([
        eq(actor_group, :assimilated_brigade_04),
        eq(place_role, :midstream_pass),
        eq(item_props, [
          name: "红绸包裹的东方红-54气缸体敲击礼",
          pulse: :alpha_metal_resonance,
          trace_left: :red_cloth_and_grease
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

  # ----------------------------------------------------------------------------
  # 4. 【三传 Three Transmissions】：因 Goal（所求之事）而发用！
  #    初传（起手触发） -> 中传（平流与滞后移易） -> 末传（下游显化归宿）
  # ----------------------------------------------------------------------------
  def sanchuano(chuchuan, zhongchuan, mochuan) do
    fresh(15, fn [
      up, mid, down, org, c0,
      regime, phase, actor, role, item,
      pulse, next_state, lag, nat_sign, inst_sign
    ] ->
      all([
        # 天地盘相合
        dipan_edgeo(up, mid, down),
        settlement_stateo(down, org, c0),
        phase_phenomenono(phase, nat_sign, inst_sign),

        # 四课取用
        artifact_actiono(actor, role, item),
        propso(item, [pulse: pulse]),

        # 发用推演
        tianpan_transitiono(c0, regime, phase, pulse, next_state, lag),

        # 初传：何时、何地、何物候下、由谁动了什么手脚
        eq(chuchuan, [
          upstream: up,
          pass: mid,
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
  # 5. 【多世界观认知透镜 Lenses】：对同一组「三传」的不同叙事投影（由 neq 保证绝不串戏）
  # ----------------------------------------------------------------------------
  def lens_projectiono(lens, chuchuan, zhongchuan, mochuan, narrative_record) do
    fresh(4, fn [phase, outcome, c0, item_name] ->
      all([
        propso(chuchuan, [phase: phase]),
        propso(zhongchuan, [c0_baseline: c0]),
        propso(mochuan, [outcome: outcome]),

        any([
          # 透镜 A：向上汇报的官样文章 (OfficialHQ)
          # 禁忌：绝不承认与 RelativeRefractory（时机失误）或 c0_collapsed（拆钟改制）有关！
          all([
            eq(lens, :OfficialHQ),
            propso(zhongchuan, [regime: :Strained]),
            eq(outcome, :premature_excitation_spike),
            neq(item_name, "岁祭镇山铜钲三叩"),
            eq(narrative_record, [
              lens: :OfficialHQ,
              stage_context: :first_breach_from_strained,
              title: "《关于白水坝十月中旬秋汛滑坡及自然损耗的结案通报》",
              claimed_cause: "十月连日秋雨导致野猪箐常规岩层疲劳滑坡（与排险爆破无关）",
              claimed_solution: "已责令302队加大炸药当量继续排险，隐患彻底消除"
            ])
          ]),

          # 透镜 B1：技术员手写内参 - 针对「从承压区(Strained)初犯击穿」的批注
          all([
            eq(lens, :TechInner),
            propso(zhongchuan, [regime: :Strained]),
            eq(phase, :RelativeRefractory),
            eq(c0, :c0_collapsed),
            eq(outcome, :premature_excitation_spike),
            eq(narrative_record, [
              lens: :TechInner,
              stage_context: :first_breach_from_strained,
              title: "《水文站工作手簿·夹页铅笔批注（一）》",
              contradiction: "通报纯属扯淡！气象台账显示当周滴雨未下。",
              real_mechanism: "此前白水坝只是恢复变慢（临界慢化 Strained），79年收走老寨铜钲后坝子没了阻尼裕度（c0=0）；302队这一炮正撞在簧片蜂鸣段（易损窗），5刻（τ=5）后直接把系统推过了失稳点！"
            ])
          ]),

          # 透镜 B2：技术员手写内参 - 针对「已在自持振荡期(Oscillating)二次误点火」的批注
          all([
            eq(lens, :TechInner),
            propso(zhongchuan, [regime: :Oscillating]),
            eq(phase, :RelativeRefractory),
            eq(c0, :c0_collapsed),
            eq(outcome, :premature_excitation_spike),
            eq(narrative_record, [
              lens: :TechInner,
              stage_context: :secondary_ignition_during_oscillation,
              title: "《水文站工作手簿·夹页铅笔批注（二）》",
              contradiction: "所谓的『追加排险爆破』是在火上浇油！",
              real_mechanism: "谷地明明已经陷入周期性自激振荡（Oscillating），302队不肯停工，在回潮易损窗追加爆破，把原本40刻后的下一波尖峰直接提前到了当场！"
            ])
          ]),

          # 透镜 C：老寨残存老人的口述 (OldZhai)
          all([
            eq(lens, :OldZhai),
            propso(zhongchuan, [regime: :Strained]),
            eq(phase, :RelativeRefractory),
            eq(outcome, :premature_excitation_spike),
            eq(narrative_record, [
              lens: :OldZhai,
              stage_context: :first_breach_from_strained,
              title: "《白水老寨幸存者口述》",
              folklore: "涧水起鱼鳞碎纹、雀鸟惊飞，那是山脉在倒换气！四队拆了寨门铜钲断了香火，勘探队又在换气口动火药，山神翻身吞了坝子。"
            ])
          ])
        ])
      ])
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
IO.puts("【推演三：绝境求生（退化新寨的邪典仪式合成）】")
IO.puts("所求之事（Goal）：白水坝已陷入自持振荡（:Oscillating），且祖传铜钲早已遗失。")
IO.puts("若要末传得「推迟尖峰（:spike_delayed_by_8_6）」，留守的四队职工（:assimilated_brigade_04）")
IO.puts("必须在什么物候与土法仪器读数下、用什么东西完成司钟？")
IO.puts("==================================================================")

results_3 =
  Logic.Core.run_all(fn q ->
    Logic.Core.fresh(7, fn [chu, zhong, mo, phase, nat_sign, inst_sign, item] ->
      Logic.Core.all([
        Voy.LiurenSandbox.sanchuano(chu, zhong, mo),
        Logic.Core.propso(chu, [
          actor: :assimilated_brigade_04,
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
          必须卡准的相位: phase,
          眼见物候口诀: nat_sign,
          土法仪器读数: inst_sign,
          替代法器与仪轨: item
        ])
      ])
    end)
  end)

IO.inspect(results_3, pretty: true, width: 100)
