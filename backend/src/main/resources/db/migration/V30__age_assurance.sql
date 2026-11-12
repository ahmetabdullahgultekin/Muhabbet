-- #751 — an age gate at registration (ADR 0029 rule 1).
--
-- Three nullable columns, because every account that exists today has none of them, and the gate
-- must not lock those accounts out: they are asked for a date of birth on their next sign-in
-- (`ageVerificationRequired` in the auth and profile responses) and declare it once through
-- PATCH /api/v1/users/me/birth-date.
--
-- birth_date           what the user declared. Written once and never changed afterwards.
-- age_assurance_level  how the age was established (SELF_DECLARED today; e-Devlet or NFC-ID later,
--                      behind the same AgeAssurancePort). NULL means "not assured": either nothing
--                      was declared yet, or what was declared is below the minimum age.
-- age_assured_at       when the assurance was made.
--
-- Nothing here is a claim that self-declaration meets the statutory age-verification duty; that is
-- counsel question Q-L3.
ALTER TABLE users ADD COLUMN birth_date DATE NULL;
ALTER TABLE users ADD COLUMN age_assurance_level VARCHAR(32) NULL;
ALTER TABLE users ADD COLUMN age_assured_at TIMESTAMP WITH TIME ZONE NULL;
