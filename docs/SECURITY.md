# Security Policy

## Supported versions

The library is pre-1.0. Security fixes are applied to the latest released version.

## Reporting a vulnerability

`Documents` persists application data, so we take security reports seriously.

**Please do not open a public issue for security vulnerabilities.**

Instead, report privately using GitHub's
[private vulnerability reporting](https://docs.github.com/en/code-security/security-advisories/guidance-on-reporting-and-writing-information-about-vulnerabilities/privately-reporting-a-security-vulnerability)
(Security tab → Report a vulnerability), or contact the maintainers directly.

Please include:

- A description of the issue and its impact
- Steps to reproduce, or a proof of concept
- Affected version(s)

We aim to acknowledge reports within a few days and will keep you updated on remediation.
Responsible disclosure is appreciated — give us reasonable time to release a fix before any
public disclosure.

## Scope notes

- The library does not encrypt data at rest in v1 (see `docs/roadmap.md`); applications
  storing sensitive data should account for this.
- Reactivity notifications are process-local; do not rely on them as a security boundary.
