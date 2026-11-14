# Care rules — evidence and sources

Every threshold in `app/src/main/java/com/dogfootprint/app/care/ComfortRules.kt` traces back to a line in this file.
Where a number is our own interpolation between sourced values it is marked **(ours)**. Last reviewed 2026-11-14.

## 1. Heat: who is at risk, and at what temperature

| Finding | Value | Source |
|---|---|---|
| Brachycephalic (flat-faced) dogs vs mesocephalic | OR **2.10** (95 % CI 1.68–2.64) for heat-related illness (HRI) | Hall, Carter & O'Neill 2020, *Sci Rep* 10:9128 (VetCompass, 905,543 dogs) |
| Breeds with highest HRI odds vs Labrador | Chow Chow 16.6 · Bulldog 14.0 · French Bulldog 6.5 · Dogue de Bordeaux 5.3 · Greyhound 4.3 · Cavalier KCS · Pug 3.2 · English Springer 2.7 · Golden Retriever 2.65 | Hall 2020, Table 2 |
| Body weight ≥ 50 kg vs < 10 kg | OR **3.42** (1.54–7.57) | Hall 2020 |
| Above breed/sex mean weight (overweight) | OR **1.42** (1.12–1.80) | Hall 2020 |
| Age ≥ 12 y vs < 2 y | OR **1.75** (1.14–2.70); all dogs > 2 y at higher odds than < 2 y | Hall 2020 |
| Thick coat matters within size (Golden vs Labrador) | OR 2.67 at similar size | Hall 2020, discussion |
| Case fatality | **14.2 %** of HRI events (one in seven) | Hall 2020 |
| Ambient temperature sufficient for HRI (majority of dogs) | **≥ 24.1 °C** | Bruchim et al. 2006, cited in Frontiers Vet Sci 2023 (PMC10601647) |
| Median ambient temperature of exertional HRI events | **16.5 °C** — heat illness is not only a hot-day problem | Hall 2020 (cited in PMC10601647) |
| Brachycephalic signs reported by owners | more severe signs **above ~19 °C**; overheating from **21–22 °C** | BOAS owner questionnaire cited in PMC10601647; Carter et al. 2020 *Vet Rec* (PMC7419075) |
| Regulatory indoor maximum for dogs | **≤ 29.5 °C (85 °F)**, never > 4 h; extra ventilation above it; humidity > 70 % compounds | USDA APHIS Animal Care tech note "Temperature requirements for dogs" |
| Dog thermoneutral zone (general) | **20–30 °C**; short-haired lower critical ~15 °C, Siberian Husky < 0 °C, Greyhound ~16–24 °C | NRC 2006 *Nutrient Requirements of Dogs and Cats* (via Purdue CAWS VA-16-W) |

## 2. Cold

| Finding | Value | Source |
|---|---|---|
| Regulatory minimum | **≥ 7.2 °C (45 °F)**, never < 4 h; **≥ 10 °C (50 °F)** for non-acclimated, short-haired or toy breeds, elderly dogs and dogs under 6 months | USDA APHIS tech note |
| Less cold-tolerant | smaller, short-legged, short-hair or hairless breeds; elderly and < 6 months | USDA APHIS tech note |
| Weather-safety scale (outdoors, no shelter) | risk from ~10 °C; "potentially unsafe" below ~4 °C for most dogs; small dogs life-threatening at −7 °C | Tufts Animal Care and Condition (TACC) scale, Patronek |
| Acclimation takes | **7–60 days** | USDA APHIS tech note |

## 3. Vehicles specifically

| Finding | Value | Source |
|---|---|---|
| Parked-car heating | outside 21 °C (70 °F) → inside 32 °C (89 °F) in **10 min**; 80 % of the rise within **30 min**; cracked windows do not help | McLaren, Null & Quinn 2005, *Pediatrics* 116:e109 |
| UK car interior over 2 years | −7.4 °C to **54.5 °C**; > 25 °C in **every month**; > 35 °C on up to 56 % of July days; peaks 14:00–17:00 | Carter et al. 2020, *Vet Rec* (PMC7419075) |
| Triggers of HRI (1,259 events) | exertional **75 %**, hot weather alone **13 %**, travelling in / left in vehicles **5 %**; older dogs and dogs with respiratory compromise had the greatest odds of environmental HRI | Hall, Carter & O'Neill 2020, *Animals* 10:1324 |
| Never leave a dog in a parked car (heat **or** cold) | — | ASPCA travel safety tips; RSPCA; AVMA |
| Restraint | "suitably restrained … seat belt harness, pet carrier, dog cage or dog guard" | UK Highway Code Rule 57 |
| Korea | 동물을 안고 운전 금지 (도로교통법 제39조 제5항), 승용차 범칙금 4만 원 | 정책브리핑 korea.kr |
| Head out of the window | not allowed | ASPCA |

## 4. Motion sickness and breaks

| Finding | Value | Source |
|---|---|---|
| Who | most common in **puppies and young dogs**; many outgrow it; ~15 % of dogs have true vestibular motion sickness | Today's Veterinary Nurse, "Preventing motion sickness in dogs" |
| Feeding | light meal **3–4 h** before departure, no food in a moving car (ASPCA); not within "a couple of hours" (TVN) | ASPCA; TVN |
| Acclimation | series of short drives, gradually longer | ASPCA; TVN |
| Medication | maropitant 2 h before travel: **7 % vomited vs 55 % placebo**; vet-prescribed only | TVN (clinical trial data) |
| Stops on long drives | every **2–3 h** for water, toilet, movement | AVMA/ASPCA guidance as commonly cited; **(ours)** shorter for puppies, seniors, motion-sick and heat-alert dogs |

## 5. Life stage (drives age-related adjustments)

| Stage | Age | Source |
|---|---|---|
| Puppy | birth → 6–9 months | AAHA 2019 Canine Life Stage Guidelines |
| Young adult | → 3–4 years | AAHA 2019 |
| Mature adult | → last 25 % of lifespan | AAHA 2019 |
| Senior | last 25 % of expected lifespan: ≈ **10 y toy/small, 9 y medium, 7 y large, 5–6 y giant** | AAHA 2019 / 2023 Senior Care Guidelines |

## 6. Sound: does "low-frequency noise" calm dogs?

**No evidence in dogs.** No controlled canine study of brown/pink noise was found; the one 2025 study on noise colours is in humans and found no arousal difference. Dogs hear from **~67 Hz** (Heffner 1983) with low-frequency thresholds similar to humans in 125–500 Hz (LSU hearing-range data), so brown noise — whose energy sits mostly below 200 Hz — is largely at or below their hearing floor. **Removed from the app.**

What does have evidence (all kennel/shelter settings, modest effects):

| Stimulus | Effect | Source |
|---|---|---|
| Audiobooks (human speech) | more resting, less barking than classical, pop, "dog-designed" music **and silence** (n = 31) | Brayley & Montrose 2016, *Appl Anim Behav Sci* 174:111 |
| Soft rock & reggae (also classical, Motown, pop) | more lying, less standing; HRV indicated lower stress, strongest for soft rock and reggae; effects held over 5 days when genres rotated (n = 38) | Bowman et al. 2017, *Physiol Behav* 171:207 |
| Classical | less stress than silence; **habituation after 7 days** of the same music | Bowman et al. 2015; Kogan 2012; Wells 2002 |
| Heavy metal | increased agitation/shaking | Kogan 2012 |
| "Species-specific" music | no better than classical | Lindig, McGreevy & Crean 2020 review, *Animals* 10:127 |

**Replacement**: "진정 모드" offers (a) calm music — bundled excerpts of the *Open Goldberg Variations* (Kimiko Ishizaka; "free of copyright, all uses allowed"), rotated so the same piece is not repeated every trip, at low volume; (b) 이야기 듣기 — a calm narration read by the vehicle's TTS (approximating the audiobook effect; evidence is for human-read books). The UI states the evidence level.

## 7. How the rules use this

- **Comfort band** (cabin temperature at the dog's seat): base 18–24 °C **(ours**, inside NRC's 20–30 °C neutral zone and under USDA's 29.5 °C cap, biased cool because HRI occurs from 16.5–24 °C). Upper bound: brachycephalic → **21 °C**; double/thick coat or large → **22 °C**; overweight → −1; ≥ 50 kg → −1; senior or puppy → −1. Lower bound: short-hair/hairless or toy → **20 °C**; senior or puppy → +1; ≥ 10 °C always (USDA sensitive-dog floor).
- **Heat alert** (spoken, red): brachycephalic ≥ 24 °C (Bruchim) or any dog ≥ 27 °C **(ours)**; in **pet-alone** mode the alert fires when climate control is off or the band is exceeded, because a parked cabin gains ~10 °C in 10 min.
- **Breaks**: adult 120 min; puppy, senior 90; motion-sick −30/−60; heat alert → 60; floor 45 **(ours**, inside the 2–3 h guidance).
- **Meal timing**: if boarding < 2 h after a meal → motion-sickness notice; < 3–4 h → mild notice for dogs with a motion-sickness history.
- **Restraint**: harness/crate reminder cites Rule 57 / 도로교통법 §39.
