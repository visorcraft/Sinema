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

Log Out is the fixed first item when present and is not part of the customizable shortcut model. Without a PIN it is omitted and Refresh becomes the first command. No confirmation is added: selecting Log Out clears only the current session's PIN verification and exits immediately, as it does today. The configured PIN remains unchanged.

## Visual treatment and accessibility

Quick actions uses compact icon-and-label command cards rather than the existing large artwork-style folder cards. Commands remain visually subordinate to media content, with readable labels, existing focus-motion accessibility behavior, overscan-safe spacing, and high-contrast focused states.

Log Out uses a subdued lock icon and distinct command styling. Its exact English accessible label is `Log Out, lock app and exit`, supplied through string resources for localization. The icon is decorative so it does not cause duplicate announcements. Other commands keep their existing labels and destinations. Compact sizing must not reduce focusability, D-pad reachability, or 10-foot readability.

The Browse row remains visually distinct from Quick actions. Existing shortcut personalization, Tonight's Mix, content cards, row titles, View All behavior, and discreet artwork handling remain unchanged. Existing screenshots are not altered.

## Focus behavior

Initial focus must always be safe and deterministic:

- When Retry Connection is visible and no previous focus is being restored, initial focus goes to Retry Connection.
- Otherwise, when the hero is available, initial focus goes to the hero.
- When the hero is absent because of Discreet Mode, Home Settings, or unavailable Continue Playing content, initial focus goes to the first visible Browse item rather than Log Out. Every visible Browse item is safe to focus because focusing never activates it; Tonight's Mix is an eligible fallback.
- If Browse has no visible items, initial focus goes to Refresh.
- Previously focused items take precedence over these defaults only when they resolve to visible, enabled, focusable targets. Hidden, disabled, or removed items use the safe fallback. Initial and fallback focus never select Log Out.

Quick actions does not wrap horizontally. Down from the hero enters Quick actions at Refresh. Down from any Quick action enters the first visible Browse item. Up from Browse enters Quick actions at Refresh, leaving Log Out one intentional Left press away. These explicit targets do not depend on horizontal geometry.

After PIN unlock with no hero, the same safe Browse fallback applies, preventing an immediate accidental re-lock. The server-error state keeps Quick actions visible and Log Out reachable. Moving between screens and refreshing rows preserves the originating command or shortcut only when it remains visible, enabled, and focusable; otherwise it uses the safe fallback.

## Implementation boundaries

Reuse `RowsSupportFragment`, `ArrayObjectAdapter`, and the existing click dispatcher. Repurpose the existing bottom settings row as Quick actions and move it above Browse; do not create a second utility row. Use a dedicated compact command presenter for Quick actions and retain `SettingsPresenter` for Browse and Retry Connection.

Quick actions reuses `SETTINGS_ROW_ID` (`7L`), and Browse retains `SHORTCUTS_ROW_ID` (`6L`). Command focus keys remain `string:Log Out`, `string:Refresh`, `string:Settings`, and `string:About`; Browse item keys are unchanged. No other row reuses these IDs or keys.

Keep `HomeShortcut`, saved shortcut order, hidden shortcuts, content-row order, and hidden rows unchanged. No new dependency, Compose migration, left rail, global navigation rewrite, or Stash API change is included.

Update the user guide and privacy documentation to describe the left-most Log Out command and the relocated Refresh/Settings/About actions.

## Failure and edge cases

- PIN absent: Log Out is omitted; all remaining commands keep their order.
- Hero absent: focus starts in Browse, never on Log Out.
- Discreet Mode: existing content hiding remains intact; commands expose no account, history, or scene metadata.
- Server failure: Retry Connection receives initial focus when no restoration target exists, Quick actions stays visible, and Log Out remains reachable.
- Empty or customized Browse row: all visible Browse entries, including Tonight's Mix, are eligible focus targets; if none is visible, use Refresh rather than Log Out.
- Refresh or layout reconciliation: restore only visible, enabled, focusable row and item keys; hidden or removed-item fallback never selects Log Out.
- Settings/About round trip: restore the originating command.

## Verification

Add focused unit and Robolectric coverage for:

- Quick actions ordering with and without a PIN.
- Fixed left-most Log Out placement independent of shortcut customization.
- Home row ordering, exact row/key identity mapping, and removal of the old bottom utility row.
- Existing Refresh, Settings, About, and immediate Log Out behavior, including preservation of the configured PIN.
- Cold launch, server-error, and PIN-unlock fallback with and without a hero.
- Discreet Mode, Tonight's Mix-only, and empty-Browse safe-focus behavior.
- Quick actions no-wrap behavior and exact hero-to-Quick, Quick-to-Browse, and Browse-to-Quick focus targets.
- Focus restoration after refresh, layout reconciliation, and Settings/About round trips, including restoration of a focused Quick action and fallback when a shortcut becomes hidden or unfocusable.
- Exact localized command-card accessibility labels, decorative icons, and focusability.

Run the project's debug unit tests, debug and release lint, and debug and release builds. Exercise directional focus on a TV-form-factor emulator if the existing test infrastructure supports it; otherwise record that geometry and physical-TV validation remain unverified. No physical-TV or live-Stash verification is implied by the standard checks.

## Non-goals

- No left navigation rail or global navigation rewrite.
- No logout confirmation or change to PIN semantics.
- No changes to server profiles, authentication, playback, home personalization data, or content queries.
- No replacement of existing screenshots.
- No version bump or release as part of this design approval.
