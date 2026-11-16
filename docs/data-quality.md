# Dataset encoding and import safety

The bundled [Zomato Restaurants Data](https://www.kaggle.com/datasets/shrutimehta/zomato-restaurants-data) CSV is attributed to Kaggle, which labels it **CC0: Public Domain**. `data/country-codes.csv` supplies the 15 code/name pairs used by the importer; the three-row `data/sample-restaurants.csv` remains the synthetic demo fixture.

## Encoding normalization

The previously bundled `zomato.csv` failed strict UTF-8 decoding (first invalid byte `ED` at offset 7,043). Reading it with UTF-8 replacement produced replacement characters in 246 physical lines. The source also contains malformed character sequences, so its original text encoding cannot be identified reliably as one coherent charset. ISO-8859-1 was used as a **lossless byte-to-character mapping**, not as a claim about the source author's encoding. The mapped text was written as UTF-8 **without a BOM**. A full byte round trip from the new UTF-8 text back to ISO-8859-1 matched the original file, and the new file passes strict UTF-8 decoding with no `U+FFFD` replacement characters. This preserves the original byte-level text; it does not guess intended spelling.

| File state | SHA-256 |
| --- | --- |
| Original CSV bytes | `100F5B85699357666B8E8E7AE57D18C723C47FE2A6B47F59809CBBE9D19D21B2` |
| Normalized UTF-8 CSV | `B76E1267CAA79B584BA2C1B9BF3D8CD0E494422C269A91F1CB7BBE25623FB25B` |

The source itself contains apparent mojibake and control-like characters in some names and locations (for example, `Brasí_lia` and `Cafí© Daniel Briand`). These are **source-data anomalies**, not UTF-8 replacement characters introduced by the conversion. There is no reliable single automatic correction for them; the importer retains them rather than inventing names. Some country/currency combinations are also inconsistent in the source, such as Philippine records carrying `Botswana Pula(P)`; currency values are preserved. The normalized file has 9,551 CSV records, all accepted by the existing parser in the verification run; 497 records have unavailable coordinates.

## Repeat imports and repair of existing documents

The importer derives deterministic UUIDs from source IDs. For each batch it checks legacy source IDs, then uses MongoDB `$setOnInsert` for deterministic IDs. A repeat import leaves existing documents, including documents later edited through the API, untouched. For duplicate source IDs inside one batch, the last valid row is used; a later duplicate in the same import may update only a document inserted during that run. Database write failures stop the import and are reported as failures, not a successful completion.

Already-imported text with `U+FFFD` is **not repaired by rerunning the importer**, because insert-only behavior protects existing data. A safe repair process is:

1. Back up the database and copy affected records to an isolated environment. Never run a blanket replacement against the development or production collection.
2. Compare each affected document with the normalized CSV by source ID. Check both legacy source-ID records and deterministic UUID records. The CSV may itself contain pre-existing mojibake, so only repair text for which the intended value is independently confirmed.
3. Review whether the document has been edited since import. Update only confirmed fields with an expected-current-value or version guard, record the changes, and verify a sample before wider rollout.
4. Reconcile counts and untouched fields afterward. Keep the original snapshot for rollback. If provenance or the intended text is uncertain, leave the record unchanged and flag it for review.

The current application does not expose an update endpoint or ship an automatic repair tool. The steps above are an operational proposal, not an action performed on existing MongoDB data.
