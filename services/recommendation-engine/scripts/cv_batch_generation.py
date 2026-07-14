"""Small orchestration helpers for deterministic CV batch generation."""

import os
from collections.abc import Callable, Sequence
from pathlib import Path
from typing import TypeVar
from uuid import uuid4


Record = TypeVar("Record")


def run_data_then_pdf_phases(
    records: Sequence[Record],
    write_data: Callable[[Record], None],
    render_pdf: Callable[[Record], None],
    after_data: Callable[[], None] | None = None,
) -> None:
    """Complete every data write before allowing the first PDF render."""
    for record in records:
        write_data(record)

    if after_data is not None:
        after_data()

    for record in records:
        render_pdf(record)


def render_pdf_atomically(
    output_path: str | Path,
    render: Callable[[str], None],
) -> None:
    """Render to a closed temporary file before replacing the target PDF."""
    target = Path(output_path)
    temporary = target.with_name(
        f".{target.stem}.{uuid4().hex}.tmp{target.suffix}"
    )

    try:
        render(str(temporary))
        os.replace(temporary, target)
    finally:
        temporary.unlink(missing_ok=True)
