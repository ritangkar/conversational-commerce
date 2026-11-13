# Demo script (SC-01 to SC-10)

Run `backend/cccore/tools-run.sh` to see all ten pass on the fake provider. For a live demo, load packs through the `ccsampledata.pack.<code>` setup options on a site with `syntheticDataOnly=true`, log in as the dealer shown, open `/ai-assistant`. The wording below matches the engine test inputs. Demo stock and the purchase list are labelled demo content; the assistant never places an order.

| Scenario | Dealer | Say | You should see | Test |
| --- | --- | --- | --- | --- |
| SC-01 Next order | `dealer-tyres` | "I need to prepare my next order. Can you help me identify what I should purchase?" | Steps (history, complements); due list with TY-1001 and overdue TY-1002, marked Estimate; TY-1004 absent because a pending order covers it; complements; why-this panel | `sc01NextOrder` |
| SC-02 Need-based discovery | `dealer-paints` | "I need something to waterproof an exterior concrete wall, medium coverage, under 3000 a unit." | PN-2001 and PN-2003, not the 4,200 item; demo stock tag | `sc02Discovery` |
| SC-03 Messy quick order | `dealer-electrical` | "Need 20 of EL-4821, 10 x 2.5mm cable 100m, 5 of EL-9910, and whatever replaces EL-1033." | List with EL-4821 x20, EL-1034 x10, EL-9910 x5; a notice that the replacement line needs a quantity | `sc03QuickOrder` |
| SC-04 Budget planner | `dealer-cement` | "I have 5 lakh to spend this month. Prepare my usual items." | List totalling at most 500,000.00; some quantities reduced or items left out | `sc04...` + property test |
| SC-05 Pattern change | `dealer-plywood` | open the page, no prompt | Welcome panel: PW-1801 down 39% (earlier average 98, recent 60) | `sc05PatternChangeWelcome` |
| SC-06 Order intelligence | `dealer-tyres` | "Show my undelivered orders from last month over 20,000 and where the latest one is." | Two orders (25,000 and 30,000); the second dealer's order never appears | `sc06...` |
| SC-07 Comparison | `dealer-paints` | select three primers (Compare) then "Compare these three exterior primers and tell me which suits a coastal area." | Table; a note that coastal suitability is not recorded for every product; explanation says it cannot verify | `sc07...` |
| SC-08 Basket review | `dealer-electrical` | add EL-9910 and EL-5050 to the list, then "Review my list before I go to checkout." | Forgotten usual EL-4821, duplicate of pending EL-5050, two complements | `sc08BasketReview` |
| SC-09 Multilingual | `dealer-cement` | "मेरा अगला ऑर्डर तैयार करने में मदद करें" | Same data shape as SC-01 with the reply translated; codes and numbers unchanged | `sc09HindiFlow` (fake translator) |
| SC-10 Industry switch | any | change `CCConfiguration.activePack` in Backoffice, wait for the cache TTL (default 10 s), reload | Pack terminology, prompts, accents and results change | `sc10PackSwitch...` (config only) |

Off-topic ("tell me a joke") and unsupported ("place the order") are redirected without tools (`offTopicAndUnsupported...`).
