# Super-admin smoke after V11 cutover. Do not paste activation tokens into logs or evidence.

1. Create a regular admin (`loginId` + reason). Expect `ADMIN` + `PENDING` and a one-time link shown only in the current page.
2. Activate via the link, set a password, log in to the workspace.
3. Confirm a pending account cannot log in. Confirm a regular `ADMIN` calling `POST /api/v1/admin/accounts` receives 403.
4. Reissue while `PENDING`. Refresh the accounts panel and confirm the token card is gone.
5. Suspend, restore, then revoke. The revoked session must fail the next write. `SUPER_ADMIN` suspend/restore/revoke must be rejected.
6. Submit one public write. Expect 200 or `PUBLIC_WRITE_RATE_LIMITED`. Confirm lifecycle/retention jobs are not double-running (single lock owner).
