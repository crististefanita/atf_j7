import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { pathToFileURL } from "node:url";
import { isMainThread, parentPort, Worker, workerData } from "node:worker_threads";

if (isMainThread) {
  const [entry, inventory] = process.argv.slice(2);
  assert.ok(entry && inventory, "Supply the Web Worker entry and YAML inventory paths");
  const resources = await readFile(inventory, "utf8");
  const worker = new Worker(new URL(import.meta.url), { workerData: pathToFileURL(entry).href });
  try {
    const response = await new Promise((resolve, reject) => {
      const timer = setTimeout(() => reject(new Error("No snapshot from Web Worker")), 50_000);
      worker.once("message", (message) => {
        clearTimeout(timer);
        resolve(message);
      });
      worker.once("error", (error) => {
        clearTimeout(timer);
        reject(error);
      });
      worker.once("exit", (status) => {
        clearTimeout(timer);
        reject(new Error("Worker exited before returning a snapshot: " + status));
      });
      worker.postMessage({ id: 1, type: "snapshot", resources });
    });
    assert.equal(response.id, 1, "Worker response ID");
    assert.equal(response.error, undefined, response.error);
    assert.ok(typeof response.snapshot === "string" && response.snapshot.length > 0, "Worker returned a snapshot");
    process.stdout.write(response.snapshot);
  } finally {
    await worker.terminate();
  }
} else {
  // Adapt only the standard browser message boundary; the production module handles the request.
  globalThis.self = globalThis;
  self.postMessage = (message) => parentPort.postMessage(message);
  await import(workerData);
  assert.equal(typeof self.onmessage, "function", "Browser Worker registered its message handler");
  parentPort.on("message", (data) => self.onmessage({ data }));
}
