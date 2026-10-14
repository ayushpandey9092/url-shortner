"""Input validation for destination URLs and custom aliases."""

import ipaddress
import re
from urllib.parse import urlsplit

ALIAS_PATTERN = re.compile(r"^[A-Za-z0-9_-]{3,30}$")

# Paths the app itself serves. A custom alias can never take one of these.
RESERVED_ALIASES = frozenset(
    {
        "api",
        "docs",
        "redoc",
        "openapi.json",
        "health",
        "static",
        "admin",
        "login",
        "logout",
        "signup",
        "register",
        "favicon.ico",
        "robots.txt",
        "index",
        "about",
        "help",
        "stats",
    }
)


class ValidationError(ValueError):
    """Raised when user input is rejected. The message is safe to show the user."""


def normalize_and_validate_url(raw: str, *, max_length: int, own_host: str | None) -> str:
    url = raw.strip()
    if not url:
        raise ValidationError("URL must not be empty.")
    if len(url) > max_length:
        raise ValidationError(f"URL is longer than {max_length} characters.")
    if any(ch.isspace() for ch in url):
        raise ValidationError("URL must not contain whitespace.")

    if "://" not in url:
        # "javascript:..." or "mailto:..." have a scheme but no "//". Reject those
        # clearly, while still allowing "example.com:8080/page" (host with a port).
        scheme, sep, rest = url.partition(":")
        if sep and "." not in scheme and not rest[:1].isdigit():
            raise ValidationError("Only http and https URLs can be shortened.")
        # Accept "example.com/page" by assuming https, as most users expect.
        url = "https://" + url

    try:
        parts = urlsplit(url)
        port = parts.port  # raises ValueError on a bad port
    except ValueError as exc:
        raise ValidationError("URL is not well formed.") from exc

    if parts.scheme.lower() not in ("http", "https"):
        raise ValidationError("Only http and https URLs can be shortened.")

    host = (parts.hostname or "").lower()
    if not host:
        raise ValidationError("URL must include a host name.")
    if not _is_plausible_host(host):
        raise ValidationError("URL host name is not valid.")
    if own_host and host == own_host.lower():
        # A short link pointing at another short link can create redirect loops.
        raise ValidationError("URLs on this shortener's own domain cannot be shortened.")
    if port is not None and not 0 < port < 65536:
        raise ValidationError("URL port is out of range.")

    return url


def _is_plausible_host(host: str) -> bool:
    try:
        ipaddress.ip_address(host)
        return True
    except ValueError:
        pass
    if host == "localhost" or "." not in host:
        return False
    labels = host.split(".")
    label_re = re.compile(r"^(?!-)[a-z0-9-]{1,63}(?<!-)$")
    # Allow IDN (punycode) labels; reject empty labels like "a..b".
    return all(label_re.match(label) or label.startswith("xn--") for label in labels)


def validate_alias(alias: str, *, looks_generated) -> str:
    alias = alias.strip()
    if not ALIAS_PATTERN.match(alias):
        raise ValidationError(
            "Custom alias must be 3-30 characters using letters, digits, '-' or '_'."
        )
    if alias.lower() in RESERVED_ALIASES:
        raise ValidationError(f"'{alias}' is reserved. Choose another alias.")
    if looks_generated(alias):
        # Keeps custom aliases and generated codes in separate namespaces, so a
        # future generated code can never clash with an alias someone picked.
        raise ValidationError(
            "Custom aliases cannot be exactly the same length as generated codes "
            "using only letters and digits. Add a '-' or '_', or change the length."
        )
    return alias
