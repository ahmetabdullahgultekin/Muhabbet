-- V33: channels get an owner and become announcement-only; bot API tokens are stored hashed.
--
-- Channels. A channel used to be created with every member, its creator included, as a plain
-- MEMBER and with announcement mode off, so nobody could administer it and every subscriber could
-- post to every other subscriber. New channels are created owned and announcement-only; this
-- brings the existing ones into line. The creator becomes OWNER where they are still subscribed.
UPDATE conversation_members m
SET role = 'OWNER'
FROM conversations c
WHERE c.id = m.conversation_id
  AND UPPER(c.type) = 'CHANNEL'
  AND m.user_id = c.created_by
  AND UPPER(m.role) = 'MEMBER';

UPDATE conversations
SET announcement_only = true
WHERE UPPER(type) = 'CHANNEL';

-- Bot tokens. Only a SHA-256 (hex) of each token is kept from now on; the token itself is shown
-- once, when it is issued. Existing tokens are converted in place, so a bot that holds its token
-- keeps working: the server hashes what it is presented with and compares hashes. A value that
-- is already 64 hex characters is a hash and is left alone, so re-running this is harmless.
UPDATE bots
SET api_token = encode(sha256(convert_to(api_token, 'UTF8')), 'hex')
WHERE api_token !~ '^[0-9a-f]{64}$';
