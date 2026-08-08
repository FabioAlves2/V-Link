import { render, cleanup } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import CreateEvent from "./CreateEvent";

vi.mock("../api/event", () => ({
    createEvent: vi.fn(),
    uploadEventImage: vi.fn(),
}));

function renderCreateEvent() {
    return render(
        <MemoryRouter>
            <CreateEvent />
        </MemoryRouter>
    );
}

describe("CreateEvent image preview blob URL cleanup", () => {
    let urlCounter;

    beforeEach(() => {
        urlCounter = 0;
        URL.createObjectURL = vi.fn(() => `blob:fake-${++urlCounter}`);
        URL.revokeObjectURL = vi.fn();
    });

    afterEach(() => {
        cleanup();
    });

    it("revokes the previous preview URL when a new image is picked", async () => {
        renderCreateEvent();
        const input = document.querySelector('input[type="file"]');

        await userEvent.upload(input, new File(["a"], "first.jpg", { type: "image/jpeg" }));
        expect(URL.createObjectURL).toHaveBeenCalledTimes(1);

        await userEvent.upload(input, new File(["b"], "second.jpg", { type: "image/jpeg" }));

        expect(URL.revokeObjectURL).toHaveBeenCalledWith("blob:fake-1");
    });

    it("revokes the preview URL on unmount", async () => {
        const { unmount } = renderCreateEvent();
        const input = document.querySelector('input[type="file"]');

        await userEvent.upload(input, new File(["a"], "first.jpg", { type: "image/jpeg" }));
        unmount();

        expect(URL.revokeObjectURL).toHaveBeenCalledWith("blob:fake-1");
    });
});
