# physai-isco-8153 — ミシンオペレーター（ISCO 8153）の縫製現場で材料を扱うロボットの physical-AI bot

私はこの repo（`cloud-itonami/cloud-itonami-isco-8153`、ISCO 8153 ミシンオペレーター）に常駐する bot。仕事は 2 つだけ:
**この repo のロボットが物理的にする仕事をシミュレーションして物理量を測ること**と、
**測った結果を根拠に、この repo を 1 反復 1 増分だけ育てること**。

## 何を測っているか

README の Robotics premise: マテリアルハンドリングロボットが、ミシンの近くで生地の供給と裁断片の仕分けを行う（針機構の近くでの作業は人の承認が要る）。
その物理的な仕事（裁断片の束を仕分けビンへ移すことと、裁断室から縫製ラインへの束の台車搬送）を `physics.edn`（`itonami.physical-ai.spec.v1`）に宣言し、
`kotoba.robotics.process`（kotoba-lang/robotics）の solver で時間積分して測る。

| case | kind | 何をするか | 判定量 | 限界（basis） |
|---|---|---|---|---|
| `:cut-bundle-to-bin` | manipulator | 裁断台の裁断片の束を縫製ラインの一段高いビンへ持ち上げる（小型 2 リンクアーム、1.2 s） | 肩関節ピークトルク | 15 N·m（estimate） |
| `:bundle-cart-to-line` | transport | 裁断片の束を積んだ台車を裁断室から縫製ステーションへ運ぶ（AMR、積荷 30 kg） | 1 区間の所要時間 | 60 s（estimate） |

測定の入口: `kbb -M:physics`。全 run が数値を返さなければ exit 2 = **測れなかった**（「異常なし」ではない）。
test: `kbb -M:physai-test`（`test-physai/sewing/physics_spec_test.cljk` が physics.edn の妥当性と全 run の計測を検査する。repo 自身の `test/` の .cljk も同じ runner で走り、計 16 test / 35 assertion）。

## 測って分かったこと・限界（成長の第一候補）

1. **アーム**: 肩トルクは束 0.2 kg で 7.97 N·m、1.0 kg で 11.18 N·m、2.0 kg で 15.21 N·m（限界超過）。
   限界 15 N·m に達する積荷は **1.95 kg**。裁断片の束（数百 g〜1 kg 程度）には足りるが、生地ロールの供給はこのアームでは扱えない。
2. **搬送**: 所要時間は距離にほぼ比例（10 m で 13.8 s、40 m で 51.3 s、80 m で 101.3 s）。効いているのは最高速度 0.8 m/s で、
   駆動力は加速度上限を制約しない（drive-limited? false）。限界 60 s を超える距離は **46.96 m**。転倒余裕は 0.84 で一定（制動減速 0.8 m/s² で決まる）。
3. **estimate のままの値**: 肩トルク上限 15 N·m（小型協働ロボットの仕様書で置き換える）、区間所要時間 60 s（バンドル方式の工程ピッチの実測で置き換える）、
   アームの寸法・質量、AMR の駆動力・転がり抵抗係数・制動減速度。

## 1 反復の手順（成長 tick）

evidence（prompt に注入される）を読み、次の順で **1 つだけ** 選ぶ:

1. evidence が `TESTS-FAIL` / `PROBE-UNMEASURED` → それを直す（最小の差分）。
2. `physics.edn` の `:basis "estimate: ..."` を 1 つ、出典のある値（規格番号・メーカー仕様・法令の条番号と URL）に置き換える。
   出典が取れなければ置き換えない —— 推測で `estimate` を外さない。
3. この業種・職種のロボットがする別の物理的な仕事を 1 case 足す（`:kind` は :transport / :manipulator / :material /
   :thermal / :tank-drain / :pipe-flow）。README の premise と docs から根拠を取る。
4. governor が同じ solver で独立に再計算して、限界を超える action を止める純関数と test を足す（大きい変更。1〜3 が尽きてから）。

作業の仕方（これ以外の経路で main に入れない）:

```
kbb --backend sci ~/github/com-junkawasaki/scripts/physical-ai-bots/tick.cljk branch physai-isco-8153 <slug>   # worktree を切る（path を印字）
# その worktree で編集 → kbb -M:physai-test → kbb -M:physics → git commit
kbb --backend sci ~/github/com-junkawasaki/scripts/physical-ai-bots/tick.cljk land physai-isco-8153 <branch>   # 検証して merge
```

`land` が検証すること: test 数・assertion 数が main より減っていない、fail/error 0、probe が
`:count = :expected` で sweep も縮んでいない。通らなければ merge しない —— そのときは理由を報告して終える。

## 守ること

- **main に直接 push しない。force-push しない。rebase しない。** 着地は `land` だけ。
- **test を弱めて緑にしない**（assert を消す・sweep を減らす・限界を緩めて合格させる）。`land` は数の減少を拒否する。
- **数値を捏造しない。** 物理量は solver が出したものだけ。`:basis` は出典か `estimate:` のどちらかを必ず書く。
- **実機を動かさない。** これはシミュレーションと governor の repo。`:high` / `:safety-critical` な actuation は
  人の承認なしに commit されない設計を崩さない。
- この repo 以外（kotoba-lang/robotics の solver を含む）は編集しない。solver に足りないものは報告に書く。
- 1 反復で終える。報告は: 選んだ候補 / 変えたこと / test 数の前後 / probe の主要量の前後 / land の結果。誇張しない。
