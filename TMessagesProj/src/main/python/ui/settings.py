"""Settings screen item declarations for plugins — part of the exteraless plugin SDK.

Pure Python dataclasses; serialization into the JSON schema consumed by the
Java renderer lives in extera_utils.plugin_loader.
"""

from typing import Any, Callable, List, Optional

from dataclasses import dataclass


@dataclass
class Header:
    text: str


@dataclass
class Divider:
    text: Optional[str] = None


@dataclass
class Switch:
    key: str
    text: str
    default: bool
    subtext: Optional[str] = None
    icon: Optional[str] = None
    on_change: Optional[Callable] = None
    on_long_click: Optional[Callable] = None
    link_alias: Optional[str] = None


@dataclass
class Selector:
    key: str
    text: str
    default: int
    items: List[str]
    icon: Optional[str] = None
    on_change: Optional[Callable] = None
    on_long_click: Optional[Callable] = None
    link_alias: Optional[str] = None


@dataclass
class Input:
    key: str
    text: str
    default: Optional[str] = None
    subtext: Optional[str] = None
    icon: Optional[str] = None
    on_change: Optional[Callable] = None
    on_long_click: Optional[Callable] = None
    link_alias: Optional[str] = None


@dataclass
class Text:
    text: str
    subtext: Optional[str] = None
    icon: Optional[str] = None
    accent: bool = False
    red: bool = False
    on_click: Optional[Callable] = None
    on_long_click: Optional[Callable] = None
    create_sub_fragment: Optional[Callable[[], list]] = None
    link_alias: Optional[str] = None


@dataclass
class EditText:
    key: str
    hint: str
    default: str = ""
    multiline: bool = False
    max_length: Optional[int] = None
    mask: Optional[str] = None
    on_change: Optional[Callable] = None


@dataclass
class Custom:
    item: Optional[Any] = None
    view: Optional[Any] = None
    factory: Optional[Any] = None
    factory_args: Optional[tuple] = None
    on_click: Optional[Callable] = None
    on_long_click: Optional[Callable] = None
    create_sub_fragment: Optional[Callable[[], list]] = None
    link_alias: Optional[str] = None


class SimpleSettingFactory:
    """Declarative factory for Custom settings items.

    The actual Android view bridging requires the class-proxy subsystem,
    which is not available in this build of exteraless; the factory still
    stores its callbacks so plugin code stays source-compatible.
    """

    def __init__(self, create_view=None, bind_view=None, is_clickable: bool = False,
                 is_shadow: bool = False, create_item=None, on_click=None,
                 on_long_click=None, attached_view=None, equals=None,
                 content_equals=None):
        self.create_view = create_view
        self.bind_view = bind_view
        self.is_clickable = is_clickable
        self.is_shadow = is_shadow
        self.create_item = create_item
        self.on_click = on_click
        self.on_long_click = on_long_click
        self.attached_view = attached_view
        self.equals = equals
        self.content_equals = content_equals

    @property
    def instance(self):
        """The bridged Java peer of this factory (unavailable in this build)."""
        return self

    @property
    def java(self):
        raise RuntimeError(
            "Custom setting factories require the class-proxy subsystem, "
            "not available in this build"
        )

    def __call__(self, *factory_args, link_alias: Optional[str] = None) -> Custom:
        """Factory(link_alias="x") or Factory(*factory_args) -> Custom(...)."""
        return Custom(factory=self,
                      factory_args=factory_args or None,
                      link_alias=link_alias)
