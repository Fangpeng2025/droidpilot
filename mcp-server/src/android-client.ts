import WebSocket from "ws";
import { EventEmitter } from "events";

export interface CommandRequest {
  id: string;
  command: string;
  params?: Record<string, unknown>;
}

export interface CommandResponse {
  id: string | null;
  success: boolean;
  data?: Record<string, unknown>;
  error?: string;
}

export class AndroidClient extends EventEmitter {
  private ws: WebSocket | null = null;
  private pendingRequests = new Map<
    string,
    {
      resolve: (value: CommandResponse) => void;
      reject: (reason: Error) => void;
      timer: NodeJS.Timeout;
    }
  >();
  private requestCounter = 0;
  private _connected = false;
  private reconnectTimer: NodeJS.Timeout | null = null;
  private deviceHost: string;
  private devicePort: number;
  private authToken?: string;

  constructor(host: string, port: number, authToken?: string) {
    super();
    this.deviceHost = host;
    this.devicePort = port;
    this.authToken = authToken;
  }

  get connected(): boolean {
    return this._connected;
  }

  get url(): string {
    return `ws://${this.deviceHost}:${this.devicePort}`;
  }

  async connect(): Promise<void> {
    return new Promise((resolve, reject) => {
      try {
        const headers: Record<string, string> = {};
        if (this.authToken) {
          headers["Authorization"] = `Bearer ${this.authToken}`;
        }

        this.ws = new WebSocket(this.url, { headers });

        const timeout = setTimeout(() => {
          this.ws?.terminate();
          reject(new Error(`Connection timeout to ${this.url}`));
        }, 10000);

        this.ws.on("open", () => {
          clearTimeout(timeout);
          this._connected = true;
          this.emit("connected");
          resolve();
        });

        this.ws.on("message", (data) => {
          try {
            const response: CommandResponse = JSON.parse(data.toString());
            if (response.id && this.pendingRequests.has(response.id)) {
              const pending = this.pendingRequests.get(response.id)!;
              clearTimeout(pending.timer);
              this.pendingRequests.delete(response.id);
              pending.resolve(response);
            }
          } catch (e) {
            this.emit("error", new Error(`Failed to parse response: ${e}`));
          }
        });

        this.ws.on("close", (code, reason) => {
          this._connected = false;
          this.rejectAllPending("Connection closed");
          this.emit("disconnected", code, reason.toString());
        });

        this.ws.on("error", (err) => {
          clearTimeout(timeout);
          if (!this._connected) {
            reject(err);
          }
          this.emit("error", err);
        });
      } catch (e) {
        reject(e);
      }
    });
  }

  disconnect(): void {
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
    this.rejectAllPending("Disconnected");
    if (this.ws) {
      this.ws.close();
      this.ws = null;
    }
    this._connected = false;
  }

  async sendCommand(
    command: string,
    params?: Record<string, unknown>,
    timeoutMs: number = 30000
  ): Promise<CommandResponse> {
    if (!this._connected || !this.ws) {
      throw new Error("Not connected to Android device");
    }

    const id = `req_${++this.requestCounter}_${Date.now()}`;
    const request: CommandRequest = { id, command, params };

    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => {
        this.pendingRequests.delete(id);
        reject(new Error(`Command '${command}' timed out after ${timeoutMs}ms`));
      }, timeoutMs);

      this.pendingRequests.set(id, { resolve, reject, timer });
      this.ws!.send(JSON.stringify(request));
    });
  }

  private rejectAllPending(reason: string): void {
    for (const [id, pending] of this.pendingRequests) {
      clearTimeout(pending.timer);
      pending.reject(new Error(reason));
    }
    this.pendingRequests.clear();
  }
}
