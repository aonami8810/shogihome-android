import { registerPlugin } from "@capacitor/core";
import { Bridge } from "./bridge.js";
import { webAPI } from "./web.js";
import { USIEngine } from "@/common/settings/usi.js";
import { GameResult } from "@/common/game/result.js";
import { TimeStates } from "@/common/game/time.js";
import { USIInfoCommand } from "@/common/game/usi.js";
import { onUSIBestMove, onUSIInfo } from "@/renderer/players/usi.js";

export interface USIEnginePlugin {
  getEngines(): Promise<{
    engines: {
      name: string;
      packageName: string;
      type: "aidl";
    }[];
  }>;
  getInfo(options: { packageName: string; timeoutSeconds?: number }): Promise<{ rawInfo: string }>;
  launch(options: { packageName: string }): Promise<{ sessionId: number }>;
  sendCommand(options: { sessionId: number; command: string }): Promise<void>;
  quit(options: { sessionId: number }): Promise<void>;
  getMachineSpec(): Promise<{ cpuCores: number; memory: number }>;
  addListener(
    eventName: "usiMessage",
    listenerFunc: (data: { sessionId: number; message: string }) => void,
  ): Promise<{ remove: () => void }>;
}

export const USIEngine = registerPlugin<USIEnginePlugin>("ShogiHomeUSIEngine");

const usiListeners: { [sessionId: number]: () => void } = {};

/**
 * USI 初期化時の sync 呼び出しを管理するステート
 */
const pendingSyncCommands = new Map<number, {
  target: "usiok" | "readyok",
  resolve: (data: string) => void,
  buffer: string[]
}>();

/**
 * 各セッションの現在の盤面（USI文字列）を保持する。
 */
const sessionUsiMap = new Map<number, string>();

export const androidAPI: Bridge = {
  ...webAPI,

  async showSelectUSIEngineDialog(): Promise<string> {
    const { engines } = await USIEngine.getEngines();
    if (engines.length === 0) {
      throw new Error("No USI engines found on this device.");
    }
    return engines[0].packageName;
  },

  async getUSIEngineInfo(path: string, timeoutSeconds: number): Promise<string> {
    const { rawInfo } = await USIEngine.getInfo({ packageName: path, timeoutSeconds });
    const lines = rawInfo.split("\n");
    let name = path;
    let author = "";
    const options: { [name: string]: any } = {};
    for (const line of lines) {
      const trimmed = line.trim();
      if (trimmed.startsWith("id name ")) {
        name = trimmed.substring(8).trim();
      } else if (trimmed.startsWith("id author ")) {
        author = trimmed.substring(10).trim();
      } else if (trimmed.startsWith("option ")) {
        const option = parseOptionLine(trimmed);
        if (option) {
          option.order = Object.keys(options).length;
          options[option.name] = option;
        }
      }
    }
    return JSON.stringify({
      uri: "es://usi-engine/" + path,
      name,
      defaultName: name,
      author,
      path,
      options,
      labels: { game: true, research: true, mate: true },
      tags: ["対局", "検討", "詰探索"],
      enableEarlyPonder: false,
    } as USIEngine);
  },

  async getUSIEngineMetadata(path: string): Promise<string> {
    return JSON.stringify({ isShellScript: false });
  },

  async sendUSIOptionButtonSignal(path: string, name: string, timeoutSeconds: number): Promise<void> {
    console.warn("sendUSIOptionButtonSignal is not implemented for Android yet.");
  },

  async usiLaunch(json: string, optionsJSON: string): Promise<number> {
    const engine: USIEngine = JSON.parse(json);
    const { sessionId } = await USIEngine.launch({ packageName: engine.path });

    const listener = await USIEngine.addListener("usiMessage", (data) => {
      if (data.sessionId === sessionId) {
        handleUSIMessage(sessionId, data.message);
      }
    });
    usiListeners[sessionId] = listener.remove;

    // usiok 待ち
    await new Promise<string>((resolve, reject) => {
      const timeout = setTimeout(() => {
        pendingSyncCommands.delete(sessionId);
        reject(new Error("Timeout waiting for usiok"));
      }, 10000);

      pendingSyncCommands.set(sessionId, {
        target: "usiok",
        buffer: [],
        resolve: (data) => {
          clearTimeout(timeout);
          if (data.includes("safe_recovery_exception")) {
            reject(new Error("Engine exception: " + data));
          } else {
            resolve(data);
          }
        }
      });

      USIEngine.sendCommand({ sessionId, command: "usi" });
    });

    // NOTE: ここで Threads や PVLength を強制設定していましたが、
    // エンジンのデフォルト設定を尊重するため、また混乱を避けるために削除しました。

    return sessionId;
  },

  async usiReady(sessionId: number): Promise<void> {
    await new Promise<string>((resolve, reject) => {
      const timeout = setTimeout(() => {
        pendingSyncCommands.delete(sessionId);
        reject(new Error("Timeout waiting for readyok"));
      }, 20000);

      pendingSyncCommands.set(sessionId, {
        target: "readyok",
        buffer: [],
        resolve: (data) => {
          clearTimeout(timeout);
          if (data.includes("safe_recovery_exception")) {
            reject(new Error("Engine exception: " + data));
          } else {
            resolve(data);
          }
        }
      });

      USIEngine.sendCommand({ sessionId, command: "isready" });
    });
  },

  async usiSetOption(sessionId: number, name: string, value: string): Promise<void> {
    await USIEngine.sendCommand({ sessionId, command: `setoption name ${name} value ${value}` });
  },

  async usiGo(sessionId: number, usi: string, timeStatesJSON: string): Promise<void> {
    const timeStates: TimeStates = JSON.parse(timeStatesJSON);
    const posCmd = usi.trim().startsWith("position") ? usi.trim() : `position ${usi.trim()}`;
    sessionUsiMap.set(sessionId, posCmd);
    await USIEngine.sendCommand({ sessionId, command: posCmd });

    const btime = (timeStates.black.time || 0) * 1000;
    const wtime = (timeStates.white.time || 0) * 1000;
    const byoyomi = (timeStates.black.byoyomi || 0) * 1000;

    // go コマンドの組み立て。以前強制していた depth 512 も削除し、
    // ユーザーの設定（またはデフォルト）に従うようにしました。
    const cmd = `go btime ${btime} wtime ${wtime} byoyomi ${byoyomi}`;
    await USIEngine.sendCommand({ sessionId, command: cmd });
  },

  async usiGoPonder(sessionId: number, usi: string, timeStatesJSON: string): Promise<void> {
    const timeStates: TimeStates = JSON.parse(timeStatesJSON);
    const posCmd = usi.trim().startsWith("position") ? usi.trim() : `position ${usi.trim()}`;
    sessionUsiMap.set(sessionId, posCmd);
    await USIEngine.sendCommand({ sessionId, command: posCmd });

    const btime = (timeStates.black.time || 0) * 1000;
    const wtime = (timeStates.white.time || 0) * 1000;
    const byoyomi = (timeStates.black.byoyomi || 0) * 1000;
    const cmd = `go ponder btime ${btime} wtime ${wtime} byoyomi ${byoyomi}`;
    await USIEngine.sendCommand({ sessionId, command: cmd });
  },

  async usiPonderHit(sessionId: number): Promise<void> {
    await USIEngine.sendCommand({ sessionId, command: "ponderhit" });
  },

  async usiGoInfinite(sessionId: number, usi: string): Promise<void> {
    const posCmd = usi.trim().startsWith("position") ? usi.trim() : `position ${usi.trim()}`;
    sessionUsiMap.set(sessionId, posCmd);
    await USIEngine.sendCommand({ sessionId, command: posCmd });
    await USIEngine.sendCommand({ sessionId, command: "go infinite" });
  },

  async usiGoMate(sessionId: number, usi: string): Promise<void> {
    const posCmd = usi.trim().startsWith("position") ? usi.trim() : `position ${usi.trim()}`;
    sessionUsiMap.set(sessionId, posCmd);
    await USIEngine.sendCommand({ sessionId, command: posCmd });
    await USIEngine.sendCommand({ sessionId, command: "go mate" });
  },

  async usiStop(sessionId: number): Promise<void> {
    await USIEngine.sendCommand({ sessionId, command: "stop" });
  },

  async usiQuit(sessionId: number): Promise<void> {
    await USIEngine.sendCommand({ sessionId, command: "quit" });
    if (usiListeners[sessionId]) {
      usiListeners[sessionId]();
      delete usiListeners[sessionId];
    }
    sessionUsiMap.delete(sessionId);
    await USIEngine.quit({ sessionId });
  },

  async usiGameover(sessionId: number, result: GameResult): Promise<void> {
    await USIEngine.sendCommand({ sessionId, command: `gameover ${result}` });
  },

  async getMachineSpec(): Promise<string> {
    const spec = await USIEngine.getMachineSpec();
    return JSON.stringify(spec);
  },
};

function handleUSIMessage(sessionId: number, message: string) {
  const line = message.trim();

  const pending = pendingSyncCommands.get(sessionId);
  if (pending) {
    pending.buffer.push(line);
    if (line === pending.target) {
      const fullResponse = pending.buffer.join("\n");
      pendingSyncCommands.delete(sessionId);
      pending.resolve(fullResponse);
      return;
    }
  }

  const currentUsi = sessionUsiMap.get(sessionId) || "";

  if (line.startsWith("bestmove ")) {
    const parts = line.split(" ");
    const bestMove = parts[1];
    const ponder = parts[2] === "ponder" ? parts[3] : undefined;
    onUSIBestMove(sessionId, currentUsi, bestMove, ponder);
  } else if (line.startsWith("info ")) {
    const info = parseInfoCommand(line.substring(5));
    if (info.string?.includes("safe_recovery_exception")) {
      const pending = pendingSyncCommands.get(sessionId);
      if (pending) {
        pendingSyncCommands.delete(sessionId);
        pending.resolve(line); // usiReady 側の resolve 内でエラーとして reject される
        return;
      }
    }
    onUSIInfo(sessionId, currentUsi, info);
  }
}

function parseInfoCommand(args: string): USIInfoCommand {
  const result: USIInfoCommand = {};
  const s = args.split(" ");
  for (let i = 0; i < s.length; i += 1) {
    switch (s[i]) {
      case "depth":
        result.depth = Number(s[i + 1]);
        i += 1;
        break;
      case "seldepth":
        result.seldepth = Number(s[i + 1]);
        i += 1;
        break;
      case "time":
        result.timeMs = Number(s[i + 1]);
        i += 1;
        break;
      case "nodes":
        result.nodes = Number(s[i + 1]);
        i += 1;
        break;
      case "pv":
        result.pv = s.slice(i + 1);
        i = s.length;
        break;
      case "multipv":
        result.multipv = Number(s[i + 1]);
        i += 1;
        break;
      case "score":
        if (s[i + 1] === "cp") {
          result.scoreCP = Number(s[i + 2]);
          i += 2;
        } else if (s[i + 1] === "mate") {
          result.scoreMate = s[i + 2] === "+" ? 10000 : s[i + 2] === "-" ? -10000 : Number(s[i + 2]);
          i += 2;
        }
        break;
      case "currmove":
        result.currmove = s[i + 1];
        i += 1;
        break;
      case "hashfull":
        result.hashfullPerMill = Number(s[i + 1]);
        i += 1;
        break;
      case "nps":
        result.nps = Number(s[i + 1]);
        i += 1;
        break;
      case "string":
        result.string = s.slice(i + 1).join(" ");
        i = s.length;
        break;
    }
  }
  return result;
}

function parseOptionLine(line: string): any {
  const parts = line.split(" ");
  const nameIndex = parts.indexOf("name");
  const typeIndex = parts.indexOf("type");
  if (nameIndex === -1 || typeIndex === -1) {
    return null;
  }
  const name = parts.slice(nameIndex + 1, typeIndex).join(" ");
  const type = parts[typeIndex + 1];
  const result: any = { name, type };

  const defaultIndex = parts.indexOf("default");
  if (defaultIndex !== -1) {
    let value: string | number = parts[defaultIndex + 1];
    if (type === "spin") {
      value = Number(value);
    }
    result.default = value;
  }

  const minIndex = parts.indexOf("min");
  if (minIndex !== -1) {
    result.min = Number(parts[minIndex + 1]);
  }

  const maxIndex = parts.indexOf("max");
  if (maxIndex !== -1) {
    result.max = Number(parts[maxIndex + 1]);
  }

  const vars: string[] = [];
  for (let i = 0; i < parts.length; i++) {
    if (parts[i] === "var") {
      vars.push(parts[i + 1]);
    }
  }
  if (vars.length > 0) {
    result.vars = vars;
  }

  return result;
}
