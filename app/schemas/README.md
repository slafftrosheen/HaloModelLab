# Room schema export

Schema version 1 is the inherited, generated schema and remains the migration source of
truth. C1 adds an explicit 1→2 migration and versioned evidence entities. Schema version 2
has intentionally not been generated or hand-authored because builds are prohibited during
this continuation. Generate and review the version 2 schema only during an authorised V0
verification step, then commit it alongside any migration correction.
