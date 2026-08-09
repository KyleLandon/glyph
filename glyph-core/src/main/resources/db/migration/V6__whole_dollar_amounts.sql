-- Currency rebase: the economy drops cents and works in whole dollars.
-- Every stored BIGINT amount was minor units (cents); divide by 100.
-- GREATEST(1, ...) keeps positive-amount CHECK constraints satisfied for
-- any sub-dollar dev-data rows (there is no production data yet).

UPDATE accounts SET balance = balance / 100;

UPDATE transactions SET amount = GREATEST(1, amount / 100);

UPDATE auction_listings
SET price       = GREATEST(1, price / 100),
    listing_fee = listing_fee / 100;

UPDATE bounties SET amount = GREATEST(1, amount / 100);

UPDATE player_kills SET bounty_amount = bounty_amount / 100;
