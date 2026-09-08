"""Cancellation and timeout coverage for async iterator cleanup helpers."""

import asyncio
import logging

import pytest

from edupilot_ai.core.async_iterators import cancel_and_wait, shielded_aclose


class ControlledClose:
    def __init__(self) -> None:
        self.started = asyncio.Event()
        self.release = asyncio.Event()
        self.closed = asyncio.Event()

    async def aclose(self) -> None:
        self.started.set()
        try:
            await self.release.wait()
        finally:
            self.closed.set()


async def test_shielded_aclose_survives_repeated_caller_cancellation() -> None:
    resource = ControlledClose()
    cleanup = asyncio.create_task(shielded_aclose(resource))
    await resource.started.wait()

    cleanup.cancel()
    await asyncio.sleep(0)
    cleanup.cancel()
    await asyncio.sleep(0)
    resource.release.set()

    with pytest.raises(asyncio.CancelledError):
        await cleanup
    assert resource.closed.is_set()


async def test_shielded_aclose_bounds_slow_cleanup(
    caplog: pytest.LogCaptureFixture,
) -> None:
    resource = ControlledClose()

    with caplog.at_level(logging.WARNING, logger="edupilot_ai.core.async_iterators"):
        await shielded_aclose(resource, timeout_seconds=0.001)
    await asyncio.wait_for(resource.closed.wait(), timeout=1)

    warning = next(
        record
        for record in caplog.records
        if record.message == "asynchronous resource cleanup exceeded grace period"
    )
    assert warning.__dict__["cleanupTimeoutSeconds"] == 0.001


async def test_cancel_and_wait_does_not_recancel_child_already_cleaning_up() -> None:
    cleanup_started = asyncio.Event()
    cleanup_finished = asyncio.Event()

    async def child() -> None:
        current_task = asyncio.current_task()
        assert current_task is not None
        current_task.cancel()
        try:
            await asyncio.sleep(0)
        except asyncio.CancelledError:
            cleanup_started.set()
            await asyncio.sleep(0.01)
            cleanup_finished.set()

    task = asyncio.create_task(child())
    await cleanup_started.wait()
    cancellation_count = task.cancelling()

    assert await cancel_and_wait(task)
    assert task.cancelling() == cancellation_count
    assert cleanup_finished.is_set()
