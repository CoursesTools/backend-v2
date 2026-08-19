# Admin Web <- Backend: Per-Referrer Custom Cashback Rates

## What Changed

Referrer cashback percentages are no longer always determined by the referrer's partnership level. Admins can now set **custom cashback rates per user**, overriding the level-based defaults. This enables special deals for influencers, partners, or individual negotiations.

---

## New Endpoint

### `PATCH /api/v1/admin/users/partnership/cashback`

Sets or clears custom cashback rate overrides for a specific user.

**Auth:** `ADMIN` role required.

#### Request Body

```json
{
  "userId": 42,
  "customCashback1": 25.0,
  "customCashback2": 10.0
}
```

| Field | Type | Required | Constraints | Description |
|---|---|---|---|---|
| `userId` | Integer | **Yes** | Must exist | Target user ID |
| `customCashback1` | BigDecimal | No | `0`–`100`, nullable | Custom rate for direct referral earnings (%). `null` = use level default |
| `customCashback2` | BigDecimal | No | `0`–`100`, nullable | Custom rate for 2nd-level referral earnings (%). `null` = use level default |

**Null semantics:** Sending `null` for a field **clears** the override — the user reverts to their partnership level's default rate. Sending `{"userId": 42, "customCashback1": null, "customCashback2": null}` resets both to defaults.

#### Response: `200 OK`

Returns the full `AdminUserReadDto` (same shape as `GET /v1/admin/users`), which now includes two new fields:

```json
{
  "id": 42,
  "email": "user@example.com",
  "tradingViewName": "trader42",
  "telegram": null,
  "countryCode": "US",
  "partnershipLevel": 3,
  "customCashback1": 25.0,
  "customCashback2": 10.0,
  "balance": 150.00,
  "referrerId": null,
  "subscriptions": [...],
  "createdAt": "2025-01-15T10:30:00"
}
```

#### Error Responses

| Status | Condition | Message |
|---|---|---|
| `400` | `userId` is null | Standard validation error |
| `400` | Rate < 0 or > 100 | Standard validation error |
| `404` | User not found | `"User not found"` |

---

## Changed Response: `GET /api/v1/admin/users`

`AdminUserReadDto` now includes two new **nullable** fields after `partnershipLevel`:

| Field | Type | Description |
|---|---|---|
| `customCashback1` | BigDecimal or null | Custom direct-referral cashback %, or `null` if using level default |
| `customCashback2` | BigDecimal or null | Custom 2nd-level cashback %, or `null` if using level default |

These fields are always present in the response. `null` means no override — the user earns at their level's standard rate.

---

## Changed Response: `GET /api/v1/users/me/partnership`

Two new fields added to `UserPartnershipReadDto`:

| Field | Type | Description |
|---|---|---|
| `effectiveCashback1` | BigDecimal | The user's actual direct-referral cashback % (custom override or level default) |
| `effectiveCashback2` | BigDecimal | The user's actual 2nd-level cashback % (custom override or level default) |

These are always non-null — they resolve to whichever rate is active (custom or level-based).

---

## UI Suggestions

### User Detail Page (Admin)

In the user info card, next to "Partnership Level: BRONZE_50 (rank 3)":
- Show custom rates if set: **"Custom Cashback: 25% / 10%"** (with an edit button)
- If no custom rates: **"Cashback: Level Default"** (with a "Set Custom" button)

### Custom Cashback Edit

A small modal or inline form:
- Two number inputs: "Direct Referral %" and "2nd Level %"
- A "Reset to Default" button that sends both as `null`
- Inputs should accept decimals (e.g., `12.5`) and validate 0–100 client-side

### Visual Indicator

Consider a badge or icon on users with custom rates in any user list, so admins can quickly spot overridden users.

---

## How It Works (Backend Logic)

1. Default: Each referrer's cashback % comes from `partnership.yml` based on their partnership level (rank 0–10)
2. Override: If `custom_cashback1` or `custom_cashback2` is set on `user_partnership`, that value is used instead of the level default
3. Each rate resolves independently — you can override just one and leave the other at default
4. Custom rates persist across level-ups (a special deal doesn't reset when the user gains referrals)
5. The actual cashback calculation is unchanged — only the rate source changed
