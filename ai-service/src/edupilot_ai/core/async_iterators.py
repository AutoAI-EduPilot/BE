"""Cancellation-safe ownership helpers for asynchronous iterators."""

import asyncio
import logging
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager
from typing import Any

logger = logging.getLogger(__name__)

_CLOSE_GRACE_SECONDS = 1.0


def _retrieve_task_result(task: asyncio.Task[Any]) -> None:
    """Observe a detached cleanup result so it cannot become an orphan warning."""
    try:
        task.result()
    except BaseException:
        return


async def _wait_for_cleanup(
    task: asyncio.Task[Any],
    *,
    timeout_seconds: float,
    cancel_on_timeout: bool,
    suppress_exceptions: bool,
) -> bool:
    """Wait for cleanup despite repeated caller cancellation, but never forever."""
    deadline = asyncio.get_running_loop().time() + timeout_seconds
    cancellation: asyncio.CancelledError | None = None
    while not task.done():
        remaining = deadline - asyncio.get_running_loop().time()
        if remaining <= 0:
            break
        try:
            done, _ = await asyncio.wait({task}, timeout=remaining)
            if not done:
                break
        except asyncio.CancelledError as error:
            cancellation = error

    if not task.done():
        if cancel_on_timeout:
            task.cancel()
        task.add_done_callback(_retrieve_task_result)
        logger.warning(
            "asynchronous resource cleanup exceeded grace period",
            extra={"cleanupTimeoutSeconds": timeout_seconds},
        )
        completed = False
    else:
        completed = True
        try:
            task.result()
        except asyncio.CancelledError:
            pass
        except Exception as error:
            if cancellation is None and not suppress_exceptions:
                raise
            logger.warning(
                "asynchronous child ended with an error during cleanup",
                extra={"exceptionType": type(error).__name__},
            )

    if cancellation is not None:
        raise cancellation
    return completed


async def shielded_aclose(
    resource: object,
    *,
    timeout_seconds: float = _CLOSE_GRACE_SECONDS,
) -> None:
    """Close a resource in an owned task, isolated from repeated cancellation."""
    aclose: Any | None = getattr(resource, "aclose", None)
    if aclose is None:
        return

    async def close() -> None:
        await aclose()

    task = asyncio.create_task(close())
    await _wait_for_cleanup(
        task,
        timeout_seconds=timeout_seconds,
        cancel_on_timeout=True,
        suppress_exceptions=False,
    )


async def cancel_and_wait(
    task: asyncio.Task[Any],
    *,
    timeout_seconds: float = _CLOSE_GRACE_SECONDS,
) -> bool:
    """Cancel an owned child once and retrieve its terminal result safely."""
    if not task.done() and task.cancelling() == 0:
        task.cancel()

    return await _wait_for_cleanup(
        task,
        timeout_seconds=timeout_seconds,
        cancel_on_timeout=False,
        suppress_exceptions=True,
    )


@asynccontextmanager
async def closing_async_iterator[ItemT](
    iterator: AsyncIterator[ItemT],
) -> AsyncIterator[AsyncIterator[ItemT]]:
    """Own an async iterator and close it when iteration stops early."""
    try:
        yield iterator
    finally:
        await shielded_aclose(iterator)
