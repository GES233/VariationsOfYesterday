defmodule Voy.World.Witness do
  @moduledoc """
  Seeded skolemization and single-card prose for a `Voy.World.Pathway`.

  The relational layer stops at symbolic slots (`{:batch, :main}`,
  `{:vessel, :main}`). This module turns one `(world_seed, branch_id, relic,
  fate)` into concrete, mutually distinct credentials:

    * one true batch number and one true industrial carrier (name + drawing
      code) drawn from the branch's period-correct pools;
    * one batch/carrier pair for the `:material_mismatch` decoy and one batch
      number for the `:destination_dead_end` decoy, never colliding with the
      true ones inside the same seed;
    * a `safe_tick` drawn from the ticks whose phase against the measured
      settled period falls inside the declared absolute-refractory window
      (`[0.20, 0.30]`).

  Choices are made with `:erlang.phash2/1` over
  `{seed, branch_id, salt, relic, fate}`, so the same save always produces the
  same batch drawer, while a different save really does produce different
  numbers -- including a different safe tap.

  ## Anti-omniscience prose

  `render_dossier/5` emits five main-chain documents plus the two decoy
  documents. Each main document knows only its own hop:

    * hop 1 sees the relic and the late-Qing omen, never a batch, a carrier, a
      team or a modern year;
    * hop 2 sees the requisition and the batch number, never the finished
      carrier, a phase or a council;
    * hop 3 sees the batch, the carrier and the physical echo it measured,
      never that the input was a mountain-guarding relic, a blast or a rite;
    * hop 4 sees the duty-cycle inversion and the safe tick, never a batch
      number or the carrier's drawing code;
    * hop 5 sees the tick and the red-cloth carrier, never a batch number, the
      Qing survey or an explanation of why the rite works.

  Every *measured* number in the prose (the two duty-cycle shares, the `+15.62`
  tick delay, the periods and the safe tick) is read from
  `Voy.World.Contract` at render time, never typed into a template. The two
  physical anchors that are not measurements -- three breaths of ring and
  twelve metres of siphon head -- come from the pathway edge's own anchor
  term; the ritual counts (nine taps, nine inches) are the ones declared in
  design note 05 section 2.2.
  """

  alias Voy.World.{Contract, Pathway, Schema}

  @relic_bronze :ancestral_bronze_zheng
  @relic_stele :carved_diversion_stele

  @bronze_batches [
    "滇垦废字（79）第04号",
    "云农调字（79）第11号",
    "垦机收字（80）第07号",
    "边森回字（79）第19号"
  ]

  @bronze_vessels [
    {"东方红-54型叁号气缸盖", "翻-79-09-丙"},
    {"丰收-35型副变速箱壳体", "铸-79-11-甲"},
    {"红旗-100型高压水泵蜗壳", "修-80-02-乙"},
    {"195型柴油机平衡飞轮盘", "翻-79-12-丁"}
  ]

  @stele_batches [
    "滇垦基字（79）第03号",
    "云水调字（79）第08号",
    "林工石字（80）第12号",
    "垦建征字（79）第15号"
  ]

  @stele_vessels [
    {"叁号冷却水塔副溢流槽", "砌-79-10-甲"},
    {"机修房尾水消力池隔墙", "水-79-12-丙"},
    {"二号水轮泵站回水弯道", "建-80-03-乙"},
    {"选矿场沉砂池虹吸底坎", "砌-79-11-丁"}
  ]

  @branches [
    %{
      relic: @relic_bronze,
      batches: @bronze_batches,
      vessels: @bronze_vessels
    },
    %{
      relic: @relic_stele,
      batches: @stele_batches,
      vessels: @stele_vessels
    }
  ]

  @templates %{
    # ==== Hop 1: late-Qing gazetteer precedent ====
    {@relic_bronze, 1} =>
      "《旧州府抚夷清丈残卷·{{pass}}铜钲案》：光绪年间清丈局强收{{up}}寨门镇山铜钲入库铸钱，" <>
        "是秋涧水起鱼鳞碎纹、山石崩落掩没局署；流官惊惧，发还铜钲，任山官依古法岁祭叩击，" <>
        "三息余音未绝而山患遂平。",
    {@relic_stele, 1} =>
      "《旧州府抚夷清丈残卷·{{pass}}分水碑案》：光绪年间清丈局毁{{pass}}分水石堰辟官田，" <>
        "暗河倒灌吞没{{down}}仓廒；复立石堰、按古槽分洪，十二米水头复通而患止，碑身刻槽如故。",

    # ==== Hop 2: requisition / custody slip (branched by c0_status) ====
    {@relic_bronze, 2, :c0_collapsed} =>
      "物资回收过磅单：{{up}}寨门旧铜器一件（铜钲）按废有色金属全数拆解过磅收缴，成色高锡，重三百四十斤；" <>
        "编入批号 {{batch_id}}，随寨门铜饰一并送机修车间翻砂。",
    {@relic_bronze, 2, :c0_intact} =>
      "林场联营代管清册：{{pass}}绝壁栈道断绝，寨门主器（铜钲）留存原地未拆；仅取其副耳高锡青铜试片四十七斤" <>
        "过磅登记，编入批号 {{batch_id}}，送机修车间翻砂配制耦合副件。",
    {@relic_stele, 2, :c0_collapsed} =>
      "物资回收过磅单：{{pass}}分水石堰条石二十七方，经丈量后全数拆毁按基建石料征用，石质灰岩、带弧槽；" <>
        "编入石料调拨批号 {{batch_id}}，着基建队运交水泵房工地。",
    {@relic_stele, 2, :c0_intact} =>
      "林场联营代管清册：{{pass}}绝壁栈道断绝，分水石堰主槽留存原地未拆；仅取其溢流副坎带弧槽条石六方" <>
        "登记入册，编入石料调拨批号 {{batch_id}}，运交水泵房工地砌筑校验副槽。",

    # ==== Hop 3: workshop casting / construction card ====
    {@relic_bronze, 3} =>
      "机修车间翻砂配料卡：领用批号 {{batch_id}} 高锡青铜料，按配比掺入生铁翻砂，" <>
        "浇铸{{vessel_name}}（图号 {{vessel_code}}）；探伤敲击，余音绵长可数三息（约1.5秒），" <>
        "与检波簧片同频谐振，非寻常灰铁铸件所有。",
    {@relic_stele, 3} =>
      "基建队施工配料卡：领用批号 {{batch_id}} 带弧槽灰岩条石，贪图省工未再改凿，" <>
        "直接砌筑{{vessel_name}}（图号 {{vessel_code}}）；试水测得溢流曲率恰合十二米水头，虹吸泄流平稳。",

    # ==== Hop 4: 302 field notebook (branched by fate, citing workshop echo/head) ====
    {@relic_bronze, 4, :Fate_Assimilated_NewZhai} =>
      "302队野外观测手簿夹页：高台期占比在起振区为 {{onset_percent}}（约{{onset_ticks}}刻，" <>
        "周期 {{onset_period}} 刻），拆器后饱和区膨胀至 {{settled_percent}}（约{{settled_ticks}}刻，" <>
        "周期 {{settled_period}} 刻），占空比反转；中段（φ=0.50）点炮反致平台阻滞 " <>
        "{{settled_delay}} 刻，全谷陷入自持振荡。唯借机修房那件具三息余音之备件，在刚落静默段" <>
        "第{{safe_tick}}格（φ≈{{safe_phase}}）激发谐振，可推迟尖峰。",
    {@relic_bronze, 4, :Fate_Annihilated_BurnoutZone} =>
      "302队野外观测手簿夹页：高台期占比由起振区 {{onset_percent}}（周期 {{onset_period}} 刻）" <>
        "膨胀至拆器后饱和区 {{settled_percent}}（周期 {{settled_period}} 刻），占空比反转；" <>
        "中段点炮致平台阻滞 {{settled_delay}} 刻后，指挥部强推超高压反向注浆冲穿烧毁膝点（a<-0.467），" <>
        "全谷锁死于灰白钙化静寂，纵在第{{safe_tick}}格（φ≈{{safe_phase}}）借机修房三息谐振备件叩测亦呈死寂直线。",
    {@relic_bronze, 4, :Fate_Deferred_ChronicSeep} =>
      "302队野外观测手簿夹页：参照拆器后饱和区高台期由 {{onset_percent}} 膨胀至 {{settled_percent}}" <>
        "（占空比反转、中段阻滞 {{settled_delay}} 刻）之界限，本沟林场低强采伐虽未跨越点火阈值，" <>
        "却因基线已失而呈阈下慢性渗漏、水位逐年阴跌；暂借机修房三息余音备件于第{{safe_tick}}格（φ≈{{safe_phase}}）" <>
        "叩测记入内参账本，待批撤离。",
    {@relic_bronze, 4, :Fate_Ancestral_ControlGroup} =>
      "302队野外观测手簿夹页：邻沟拆器处高台期由 {{onset_percent}} 膨胀至 {{settled_percent}}" <>
        "（占空比反转、中段阻滞 {{settled_delay}} 刻）；本沟幸赖绝壁主器留存，林场低强扰动全数落入" <>
        "静息吸引域，仅借机修房所铸三息余音副件于第{{safe_tick}}格（φ≈{{safe_phase}}）作对照校验。",
    {@relic_bronze, 4, :Fate_Tolerated_FalseAlarm} =>
      "302队野外观测手簿夹页：若基线散失则高台期必由 {{onset_percent}} 膨胀至 {{settled_percent}}" <>
        "（占空比反转、中段阻滞 {{settled_delay}} 刻）；然本沟绝壁主器完好，四十七次周期点炮虽撞在易损窗" <>
        "却尽数被宽容区吸收（虚警四十七次未成灾），第{{safe_tick}}格（φ≈{{safe_phase}}）三息谐振副件读数恒稳。",
    {@relic_bronze, 4, :Fate_Ascended_SpatialRift} =>
      "302队野外观测手簿夹页：对照起振区 {{onset_percent}} 与饱和区 {{settled_percent}} 之占空比反转" <>
        "（中段阻滞 {{settled_delay}} 刻），本沟主器虽存，源头超高压强行注浆却将激发推至极值空间裂隙，" <>
        "唯在第{{safe_tick}}格（φ≈{{safe_phase}}）借机修房三息余音副件与山口共鸣，方免裂口下移。",
    {@relic_stele, 4, :Fate_Assimilated_NewZhai} =>
      "302队野外观测手簿夹页：高台期占比在起振区为 {{onset_percent}}（约{{onset_ticks}}刻，" <>
        "周期 {{onset_period}} 刻），毁堰后溶洞承压区膨胀至 {{settled_percent}}（约{{settled_ticks}}刻，" <>
        "周期 {{settled_period}} 刻），占空比反转；中段（φ=0.50）爆破反致暗河憋压阻滞 " <>
        "{{settled_delay}} 刻，全谷自持振荡。唯借水泵房那处合十二米水头之溢流设施，于静默段" <>
        "第{{safe_tick}}格（φ≈{{safe_phase}}）开启虹吸闸门，可泄压推迟尖峰。",
    {@relic_stele, 4, :Fate_Annihilated_BurnoutZone} =>
      "302队野外观测手簿夹页：高台期占比由起振区 {{onset_percent}} 膨胀至毁堰后承压区 {{settled_percent}}" <>
        "（占空比反转、中段憋压阻滞 {{settled_delay}} 刻）；指挥部继以超高压水泥封井冲穿烧毁膝点（a<-0.467），" <>
        "暗河彻底钙化死寂，纵在第{{safe_tick}}格（φ≈{{safe_phase}}）开启十二米水头溢流副槽亦无滴水回流。",
    {@relic_stele, 4, :Fate_Deferred_ChronicSeep} =>
      "302队野外观测手簿夹页：参照毁堰后高台期由 {{onset_percent}} 膨胀至 {{settled_percent}}" <>
        "（占空比反转、中段阻滞 {{settled_delay}} 刻）之界限，本沟低强采伐虽未触发暗河喷涌，" <>
        "却因主堰已毁而致溶洞慢性渗漏、潜水位逐年阴跌；暂于第{{safe_tick}}格（φ≈{{safe_phase}}）" <>
        "试启十二米水头溢流槽测渗，记入内参待撤。",
    {@relic_stele, 4, :Fate_Ancestral_ControlGroup} =>
      "302队野外观测手簿夹页：邻沟毁堰处高台期由 {{onset_percent}} 膨胀至 {{settled_percent}}" <>
        "（占空比反转、中段阻滞 {{settled_delay}} 刻）；本沟幸赖绝壁主堰留存，低强扰动尽数落入宽容区，" <>
        "仅借水泵房十二米水头副槽于第{{safe_tick}}格（φ≈{{safe_phase}}）作平水校验。",
    {@relic_stele, 4, :Fate_Tolerated_FalseAlarm} =>
      "302队野外观测手簿夹页：若主堰拆毁则高台期必由 {{onset_percent}} 膨胀至 {{settled_percent}}" <>
        "（占空比反转、中段阻滞 {{settled_delay}} 刻）；然本沟古堰完好，四十七次爆破虽擦过临界" <>
        "却尽为虹吸宽容区吸收（四十七次虚警未成灾），第{{safe_tick}}格（φ≈{{safe_phase}}）副槽水头恒稳。",
    {@relic_stele, 4, :Fate_Ascended_SpatialRift} =>
      "302队野外观测手簿夹页：对照起振区 {{onset_percent}} 与承压区 {{settled_percent}} 之占空比反转" <>
        "（中段阻滞 {{settled_delay}} 刻），本沟主堰虽在，源头超高压注浆却将溶洞应力推至极值空间裂隙，" <>
        "唯于第{{safe_tick}}格（φ≈{{safe_phase}}）开启十二米水头副槽分流，可止裂隙外扩。",

    # ==== Hop 5: council / watchpost / quarantine ordinance (branched by fate) ====
    {@relic_bronze, 5, :Fate_Assimilated_NewZhai} =>
      "留守议事会交接班死规：每班穿劳保服，盯验山垂架簧片落定格；见垂架落定至第{{safe_tick}}格，" <>
        "即持八磅锤叩击红绸所悬之{{vessel_name}}（{{vessel_code}}）九下，推迟当旬落石。",
    {@relic_bronze, 5, :Fate_Annihilated_BurnoutZone} =>
      "灰白封禁区水泥封井令：自源头至坝区尽化灰白死石、草木虫声俱绝；着将机修房内{{vessel_name}}" <>
        "（{{vessel_code}}）于验山匣卡死之第{{safe_tick}}格原位浇筑水泥封存，严禁生人入谷。",
    {@relic_bronze, 5, :Fate_Deferred_ChronicSeep} =>
      "缓撤监测哨双账本守则：场部撤离报告待批期间，值守员每逢垂架落至第{{safe_tick}}格，" <>
        "须轻叩机修房{{vessel_name}}（{{vessel_code}}）验听地声，对外填报资源枯竭，对内实录慢渗。",
    {@relic_bronze, 5, :Fate_Ancestral_ControlGroup} =>
      "老寨护山联营旧约：山口古器照旧岁祭，林场机修房所配之{{vessel_name}}（{{vessel_code}}）" <>
        "每逢垂架行至第{{safe_tick}}格由守山人轻叩一记作平水应答，严禁外人擅动。",
    {@relic_bronze, 5, :Fate_Tolerated_FalseAlarm} =>
      "绝壁观山寮核销条规：凡遇上游点炮虚警，待垂架回落至第{{safe_tick}}格，查验机修房" <>
        "{{vessel_name}}（{{vessel_code}}）余音无异，即予核销归档，严禁妄拆山口旧物。",
    {@relic_bronze, 5, :Fate_Ascended_SpatialRift} =>
      "山官火塘封山铁誓：源头掩体已化黑影裂隙，见高瘦黑影搬石切勿直视；每逢垂架落至第{{safe_tick}}格，" <>
        "值守人须叩响山口旁所设{{vessel_name}}（{{vessel_code}}）九记以应古器，日夜不得断人。",
    {@relic_stele, 5, :Fate_Assimilated_NewZhai} =>
      "留守议事会交接班死规：每班穿劳保服按点名上闸，见水位计满格刚退、验山匣静默至第{{safe_tick}}格，" <>
        "即拉起红绸所系之{{vessel_name}}（{{vessel_code}}）铸铁闸板九寸，放暗河回头水。",
    {@relic_stele, 5, :Fate_Annihilated_BurnoutZone} =>
      "灰白封禁区水泥封井令：自溶洞至坝区尽化灰白钙化死石、滴水全无；着将水泵房{{vessel_name}}" <>
        "（{{vessel_code}}）闸口于水位计卡死之第{{safe_tick}}格原位灌浆焊死，全谷永久封禁。",
    {@relic_stele, 5, :Fate_Deferred_ChronicSeep} =>
      "缓撤监测哨双账本守则：撤离方案待批期间，值守员每逢验山匣落至第{{safe_tick}}格，" <>
        "须开启{{vessel_name}}（{{vessel_code}}）测读潜水位阴跌刻度，外报正常枯竭，内记慢渗。",
    {@relic_stele, 5, :Fate_Ancestral_ControlGroup} =>
      "老寨护山联营旧约：山口古堰照旧岁修，水泵房所砌之{{vessel_name}}（{{vessel_code}}）" <>
        "每逢第{{safe_tick}}格由守堰人验看平水过流，严禁外人擅改刻槽。",
    {@relic_stele, 5, :Fate_Tolerated_FalseAlarm} =>
      "绝壁观山寮核销条规：凡遇上游点炮虚警，待验山匣回落至第{{safe_tick}}格，查验水泵房" <>
        "{{vessel_name}}（{{vessel_code}}）溢流平稳，即予核销归档，严禁妄动山口古堰。",
    {@relic_stele, 5, :Fate_Ascended_SpatialRift} =>
      "山官火塘封山铁誓：源头溶洞已化黑影裂隙，见高瘦黑影搬石切勿直视；每逢验山匣落至第{{safe_tick}}格，" <>
        "值守人须提拉{{vessel_name}}（{{vessel_code}}）分水闸九寸以应古堰，日夜不得断人。",

    # ==== Decoy cards ====
    {@relic_bronze, :material_mismatch} =>
      "机修车间翻砂配料卡：收进批号 {{decoy_a_batch}} 普通生铁农具一批（犁铧、锄板、铁锅），" <>
        "回炉化铁，浇铸{{decoy_a_vessel_name}}（图号 {{decoy_a_vessel_code}}）；探伤敲击，" <>
        "声短而闷，无余音。",
    {@relic_bronze, :destination_dead_end} =>
      "物资回收过磅单：{{up}}旧渠石料一批（内含铜饰三件）验收入库，编入批号 {{decoy_b_batch}}；" <>
        "因本场熔炉不足，全数外运省局冶炼厂，回单另附。",
    {@relic_stele, :material_mismatch} =>
      "基建队施工配料卡：领用批号 {{decoy_a_batch}} 普通毛石一批（无弧槽、未过筛），因省料直接砌筑" <>
        "{{decoy_a_vessel_name}}（图号 {{decoy_a_vessel_code}}）；试水渗漏，未成虹吸。",
    {@relic_stele, :destination_dead_end} =>
      "物资回收过磅单：{{pass}}旧渠条石一批（内含带槽残碑三块）验收入库，编入批号 {{decoy_b_batch}}；" <>
        "因本场石料指标不足，全数外调省水利工程处，回单另附。"
  }

  @titles %{
    {@relic_bronze, 1} => "《旧州府抚夷清丈残卷》残页",
    {@relic_bronze, 2} => "物资回收与代管过磅单",
    {@relic_bronze, 3} => "机修车间翻砂配料卡",
    {@relic_bronze, 4} => "302队野外观测手簿（夹页）",
    {@relic_bronze, 5} => "山谷现行守山与封禁条规",
    {@relic_bronze, :material_mismatch} => "机修车间翻砂配料卡（另册）",
    {@relic_bronze, :destination_dead_end} => "物资回收过磅单（外运）",
    {@relic_stele, 1} => "《旧州府抚夷清丈残卷》残页",
    {@relic_stele, 2} => "物资回收与代管过磅单",
    {@relic_stele, 3} => "基建队施工配料卡",
    {@relic_stele, 4} => "302队野外观测手簿（夹页）",
    {@relic_stele, 5} => "山谷现行守山与封禁条规",
    {@relic_stele, :material_mismatch} => "基建队施工配料卡（另册）",
    {@relic_stele, :destination_dead_end} => "物资回收过磅单（外运）"
  }

  @doc """
  Bind one seed to concrete credentials for a `(relic, fate)` pathway.

  Raises on an unknown relic or fate: a witness without a chain is a caller
  bug, not a world state.
  """
  @spec bind(integer(), term(), atom(), atom() | String.t()) :: map()
  def bind(seed, branch_id, relic, fate) do
    spec = branch!(relic)
    fate = fate!(fate)
    dynamics = dynamics()
    relation_decoys = Pathway.decoys(relic)

    material_decoy =
      Enum.find(relation_decoys, &(&1.attrs[:decoy_class] == :material_mismatch))

    destination_decoy =
      Enum.find(relation_decoys, &(&1.attrs[:decoy_class] == :destination_dead_end))

    [batch_id, decoy_a_batch, decoy_b_batch] =
      rotate(
        spec.batches,
        hash(seed, branch_id, :batch, relic, fate, length(spec.batches)),
        3
      )

    [vessel, decoy_vessel] =
      rotate(
        spec.vessels,
        hash(seed, branch_id, :vessel, relic, fate, length(spec.vessels)),
        2
      )

    ticks = safe_ticks(dynamics)
    safe_tick = Enum.at(ticks, hash(seed, branch_id, :safe_tick, relic, fate, length(ticks)))

    %{
      seed: seed,
      branch_id: branch_id,
      relic: relic,
      fate: fate,
      batch_id: batch_id,
      vessel_name: elem(vessel, 0),
      vessel_code: elem(vessel, 1),
      safe_tick: safe_tick,
      safe_phase: safe_tick / dynamics.settled_period,
      decoys: %{
        material_mismatch: %{
          batch_id: decoy_a_batch,
          vessel_name: elem(decoy_vessel, 0),
          vessel_code: elem(decoy_vessel, 1),
          material: material_decoy.attrs[:material],
          anchor: material_decoy.attrs[:anchor]
        },
        destination_dead_end: %{
          batch_id: decoy_b_batch,
          material: destination_decoy.attrs[:material],
          disposition: destination_decoy.attrs[:disposition]
        }
      },
      dynamics: dynamics
    }
  end

  @doc """
  The safe taps the declared absolute-refractory window allows.

  Derived from the contract: integer ticks whose phase against the measured
  settled period lies in the declared `:absolute` window. For the shipped
  contract this is `[8, 9, 10, 11]` (phases roughly `0.20`-`0.28`).
  """
  @spec safe_ticks() :: [pos_integer()]
  def safe_ticks, do: safe_ticks(dynamics())

  @doc "The five main documents and the two decoy documents of one dossier."
  @spec render_dossier(integer(), term(), atom(), atom() | String.t(), term()) :: map()
  def render_dossier(seed, branch_id, relic, fate, names \\ []) do
    binding = bind(seed, branch_id, relic, fate)
    {:ok, %{chain: chain}} = Pathway.compile_chain(relic, binding.fate)
    decoys = Pathway.decoys(relic)
    interpolation = interpolation(binding, places(names))

    main =
      Enum.map(chain, fn edge ->
        document(edge, interpolation, :main, relic, binding.fate)
      end)

    decoy_documents =
      Enum.map(decoys, fn edge ->
        document(edge, interpolation, :decoy, relic, binding.fate)
      end)

    %{
      seed: seed,
      branch_id: branch_id,
      relic: relic,
      fate: binding.fate,
      binding: binding,
      documents: main ++ decoy_documents
    }
  end

  # ==== deterministic skolemization ==== //

  defp hash(seed, branch_id, salt, relic, fate, size) do
    :erlang.phash2({seed, branch_id, salt, relic, fate})
    |> rem(size)
  end

  defp rotate(items, offset, count) do
    size = length(items)

    Enum.map(0..(count - 1), fn position ->
      Enum.at(items, rem(offset + position, size))
    end)
  end

  defp safe_ticks(%{window: {low, high}, settled_period: period}) do
    last = period |> Kernel.*(high) |> Float.ceil() |> trunc()

    Enum.filter(1..last, fn tick ->
      phase = tick / period
      phase >= low and phase <= high
    end)
  end

  # ==== prose ==== //

  defp document(edge, interpolation, role, relic, fate) do
    title_key =
      case role do
        :main -> {relic, edge.hop}
        :decoy -> {relic, edge.attrs[:decoy_class]}
      end

    template_key =
      case {role, edge.hop} do
        {:decoy, _} -> {relic, edge.attrs[:decoy_class]}
        {:main, 2} -> {relic, 2, edge.attrs[:c0_status]}
        {:main, hop} when hop in [4, 5] -> {relic, hop, fate}
        {:main, hop} -> {relic, hop}
      end

    %{
      hop: edge.hop,
      role: role,
      doc_kind: edge.doc_kind,
      lens: edge.lens,
      decoy_class: edge.attrs[:decoy_class],
      title: Map.fetch!(@titles, title_key),
      body: render!(Map.fetch!(@templates, template_key), interpolation)
    }
  end

  defp interpolation(binding, places) do
    dynamics = binding.dynamics
    decoys = binding.decoys

    [
      up: places.up,
      pass: places.pass,
      down: places.down,
      batch_id: binding.batch_id,
      vessel_name: binding.vessel_name,
      vessel_code: binding.vessel_code,
      onset_period: decimals(dynamics.onset_period, 1),
      onset_percent: dynamics.onset_percent,
      onset_ticks: decimals(dynamics.onset_period * dynamics.onset_fraction, 1),
      settled_period: decimals(dynamics.settled_period, 1),
      settled_percent: dynamics.settled_percent,
      settled_ticks: decimals(dynamics.settled_period * dynamics.settled_fraction, 1),
      settled_delay: signed(dynamics.settled_mid_cycle_delta, 2),
      safe_tick: Integer.to_string(binding.safe_tick),
      safe_phase: decimals(binding.safe_phase, 2),
      decoy_a_batch: decoys.material_mismatch.batch_id,
      decoy_a_vessel_name: decoys.material_mismatch.vessel_name,
      decoy_a_vessel_code: decoys.material_mismatch.vessel_code,
      decoy_b_batch: decoys.destination_dead_end.batch_id
    ]
  end

  defp render!(template, interpolation) do
    rendered =
      Enum.reduce(interpolation, template, fn {name, value}, acc ->
        String.replace(acc, "{{#{name}}}", to_string(value))
      end)

    case Regex.run(~r/\{\{[a-z0-9_.]+\}\}/, rendered) do
      nil -> rendered
      [leftover] -> raise ArgumentError, "unbound placeholder #{inspect(leftover)}"
    end
  end

  defp places(names) do
    %{
      up: place(names, :up, "上游"),
      pass: place(names, :pass, "隘口"),
      down: place(names, :down, "谷地")
    }
  end

  defp place(names, key, default) when is_list(names) do
    names |> Keyword.get(key, default) |> to_string()
  end

  defp place(names, key, default) when is_map(names) do
    value = Map.get(names, key) || Map.get(names, Atom.to_string(key))
    to_string(value || default)
  end

  defp place(_names, _key, default), do: default

  defp decimals(number, places) do
    :erlang.float_to_binary(number * 1.0, decimals: places)
  end

  defp signed(number, places) do
    sign = if number < 0, do: "-", else: "+"
    sign <> decimals(abs(number), places)
  end

  defp percent(fraction) do
    :erlang.float_to_binary(fraction * 100, decimals: 1) <> "%"
  end

  # ==== contract readings ==== //

  defp dynamics do
    contract = Contract.load!()
    onset = Contract.onset_comparison(contract)
    settled = Contract.oscillator(contract)
    {:ok, collapsed} = Contract.c0_truth(contract, :c0_collapsed)
    {:ok, delayed} = Contract.outcome(contract, :alpha_shock, :relative)
    {low, high} = Map.fetch!(Contract.windows(contract), :absolute)

    %{
      onset_period: onset.period,
      onset_fraction: onset.plateau_fraction,
      onset_percent: percent(onset.plateau_fraction),
      onset_mid_cycle_delta: onset.mid_cycle_delta,
      settled_period: collapsed.settled_period,
      settled_fraction: settled.plateau_fraction,
      settled_percent: percent(settled.plateau_fraction),
      settled_mid_cycle_delta: delayed.delta,
      window: {low, high}
    }
  end

  # ==== inputs ==== //

  defp branch!(relic) do
    case Enum.find(@branches, &(&1.relic == relic)) do
      nil -> raise ArgumentError, "unknown relic: #{inspect(relic)}"
      branch -> branch
    end
  end

  defp fate!(fate) do
    case Schema.normalize_fate(fate) do
      {:ok, normalized} ->
        normalized

      {:error, reason} ->
        raise ArgumentError, "unknown fate #{inspect(fate)}: #{inspect(reason)}"
    end
  end
end
