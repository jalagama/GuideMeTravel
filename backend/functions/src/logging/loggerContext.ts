import { ConsoleGuideMeLogger } from "./ConsoleGuideMeLogger";
import { GuideMeLogger } from "./GuideMeLogger";

let activeLogger: GuideMeLogger = new ConsoleGuideMeLogger();

export function getGuideMeLogger(): GuideMeLogger {
  return activeLogger;
}

export function setGuideMeLogger(logger: GuideMeLogger): void {
  activeLogger = logger;
}

export function resetGuideMeLogger(): void {
  activeLogger = new ConsoleGuideMeLogger();
}
