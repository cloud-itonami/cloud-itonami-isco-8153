# cloud-itonami-isco-8153

Open Occupation Blueprint for **ISCO-08 8153**: Sewing Machine Operators.

**Maturity: `:implemented`** — SewingAdvisor ⊣ SewingOperationsGovernor
as a langgraph StateGraph (`intake → advise → govern → decide →
commit/hold`, human-approval interrupt), modeled on
cloud-itonami-isco-4311's bookkeeping actor. 14 tests / 30 assertions
green. The governor never dispatches hardware — it only gates what
the material-handling robot below may execute.

The sew-run HARD invariants — a spec band and arithmetic, not operator
feel:

1. **Stitch-density band** — the measured stitch density must fall
   inside the registered [min, max] band.
2. **Seam-deviation ceiling** — the measured seam deviation must not
   exceed the registered ceiling.

`:approve-needle-mechanism-operation` and
`:clear-quality-inspection-failure` **always** escalate to human
sign-off regardless of confidence, per this repo's Trust Controls
(business-model.md).

This repository designs a forkable OSS business for an independent sewing machine operator: a material-handling robot performs fabric feeding and cut-piece sorting under a governor-gated actor, so the operator keeps their own production and quality records instead of renting a closed garment-production SaaS.

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot performs
the physical domain work**. Here a material-handling robot performs fabric feeding and cut-piece sorting near sewing equipment under an actor that proposes
actions and an independent **Sewing Operations Governor** that gates them. The governor never
dispatches hardware itself; `:high`/`:safety-critical` actions (such as
operating near the sewing needle mechanism, or clearing a quality-inspection failure) require human sign-off.

A live sample of the operator console (robotics safety console, shared template) is rendered in [docs/samples/operator-console.html](docs/samples/operator-console.html) — pure-data HTML output of `kotoba.robotics.ui`.

## Core Contract

```text
production order + pattern spec + quality standard
        |
        v
Sewing Advisor -> Sewing Operations Governor -> sew/inspect, or human sign-off
        |
        v
robot actions (gated) + operating records + audit ledger
```

No automated advice can dispatch a robot action the governor refuses, suppress
an operating record, or disclose sensitive data without governor approval and
audit evidence.

## Capability layer

Resolves via [`kotoba-lang/occupation`](https://github.com/kotoba-lang/occupation)
(ISCO-08 `8153`). Required capabilities:

- :robotics
- :telemetry
- :dmn
- :bpmn
- :audit-ledger

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md).

## License

AGPL-3.0-or-later.
