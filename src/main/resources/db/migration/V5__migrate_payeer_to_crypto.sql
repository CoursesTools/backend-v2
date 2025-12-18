-- Migrate all PAYEER payment methods to CRYPTO
-- This migration changes payment_method from PAYEER to CRYPTO for existing data

-- Update users_subscriptions table
UPDATE users_subscriptions
SET payment_method = 'CRYPTO'
WHERE payment_method = 'PAYEER';