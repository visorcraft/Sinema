# Home command row and fast lock design

Date: 2026-11-02
Status: Approved design, pending implementation planning

## Context and goals

Sinema's home screen currently places an optional Continue Playing hero above a long customizable shortcut row, configurable content rows, and a bottom Refresh/Settings/About row. When PIN protection is enabled, Log Out is appended after all shortcuts, making the safe lock action slow and positionally unstable.

The approved change makes Log Out the fixed left-most command and improves the home hierarchy without replacing the Leanback navigation architecture. The change must preserve shortcut and content-row customization, Discreet Mode, existing destinations, and reliable D-pad focus restoration.

OpenCode and MiniMax independently reviewed three directions: minimal reordering, a consolidated top command row, and a fixed left navigation rail. Both recommended the consolidated command row with restrained styling. The rail was rejected because it would consume content width and require bespoke focus management around `RowsSupportFragment` and the existing focus-restoration code.

## Home structure

Home rows appear in this order:

1. Retry Connection, only when the existing full-dashboard error state requires it.
2. The existing optional Continue Playing hero, subject to current Home Settings and Discreet Mode rules.
3. A compact Quick actions row.
4. A Browse row containing existing visible user shortcuts in their saved order, followed by Tonight's Mix.
5. Existing configurable content rows in their saved order.

The current bottom Refresh/Settings/About row is removed. Its actions move to Quick actions with stable identities and unchanged behavior.

Quick actions contains:

1. Log Out, only when a PIN is configured.
2. Refresh.
3. Settings.
4. About.

Log Out is the fixed first item when present and is not part of the customizable shortcut model. Without a PIN it is omitted and Refresh becomes the first command. No confirmation is added: selecting Log Out clears PIN verification and exits immediately, as it does today.

## Visual treatment and accessibility

Quick actions uses compact icon-and-label command cards rather than the existing large artwork-style folder cards. Commands remain visually subordinate to media content, with readable labels, existing focus-motion accessibility behavior, overscan-safe spacing, and high-contrast focused states.

Log Out uses a subdued lock icon and distinct command styling. Its accessible label communicates both the displayed name and lock behavior without relying on emoji. Other commands keep their existing labels and destinations. Compact sizing must not reduce focusability, D-pad reachability, or 10-foot readability.

The Browse row remains visually distinct from Quick actions. Existing shortcut personalization, Tonight's Mix, content cards, row titles, View All behavior, and discreet artwork handling remain unchanged. Existing screenshots are not altered.

## Focus behavior

Initial focus must always be safe and deterministic:

- When the hero is available, initial focus goes to the hero.
- When the hero is absent because of Discreet Mode, Home Settings, or unavailable Continue Playing content, initial focus goes to the first Browse command rather than Log Out.
- If a previously focused item is being restored, restoration takes precedence over the default only when it resolves to an existing item. Fallback must never select Log Out.

Quick actions does not wrap horizontally. Down enters Browse, and Up from Browse returns predictably to Quick actions using normal Leanback focus behavior and existing focus restoration. Moving between screens and refreshing rows preserves the originating command or shortcut when that item still exists; otherwise it uses the safe fallback.

After PIN unlock with no hero, the same safe Browse fallback applies, preventing an immediate accidental re-lock. The server-error state does not make Log Out unreachable.

## Implementation boundaries

Reuse `RowsSupportFragment`, `ArrayObjectAdapter`, stable row IDs, and the existing click dispatcher. Relocate the existing settings row rather than introducing a parallel navigation system. Use a dedicated compact command presenter for Quick actions and retain `SettingsPresenter` for Browse and Retry Connection.

Keep `HomeShortcut`, saved shortcut order, hidden shortcuts, content-row order, and hidden rows unchanged. Reuse the existing settings row ID and string-based command keys so focus restoration remains stable. No new dependency, Compose migration, left rail, global navigation rewrite, or Stash API change is included.

Update the user guide and privacy documentation to describe the left-most Log Out command and the relocated Refresh/Settings/About actions.

## Failure and edge cases

- PIN absent: Log Out is omitted; all remaining commands keep their order.
- Hero absent: focus starts in Browse, never on Log Out.
- Discreet Mode: existing content hiding remains intact; commands expose no account, history, or scene metadata.
- Server failure: Retry Connection keeps its existing behavior and Log Out remains reachable.
- Empty or customized Browse row: use the first available non-destructive destination; if none exists, use Refresh rather than Log Out.
- Refresh or layout reconciliation: stable row and item keys prevent unintended focus jumps; removed-item fallback never selects Log Out.
- Settings/About round trip: restore the originating command.

## Verification

Add focused unit and Robolectric coverage for:

- Quick actions ordering with and without a PIN.
- Fixed left-most Log Out placement independent of shortcut customization.
- Home row ordering and removal of the old bottom utility row.
- Existing Refresh, Settings, About, and immediate Log Out behavior.
- Cold launch and PIN-unlock fallback with and without a hero.
- Discreet Mode and empty-Browse safe-focus behavior.
- Focus restoration after refresh, layout reconciliation, and Settings/About round trips.
- Command-card accessibility labels and focusability.

Run the project's debug unit tests, debug and release lint, and debug and release builds. No physical-TV or live-Stash verification is implied by those checks.

## Non-goals

- No left navigation rail or global navigation rewrite.
- No logout confirmation or change to PIN semantics.
- No changes to server profiles, authentication, playback, home personalization data, or content queries.
- No replacement of existing screenshots.
- No version bump or release as part of this design approval.
