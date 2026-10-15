export interface FirmwarePart {
  address: number | string;
  file: string;
  sha256?: string;
  sizeBytes?: number;
}

export interface FirmwareRelease {
  version: string;
  releaseDate?: string;
  signed: boolean;
  description: string;
  /** Primary application binary path (target offset 0x10000) */
  app: string;
  /** Bootloader binary path (target offset 0x0000) */
  bootloader?: string;
  /** Partition table binary path (target offset 0x8000) */
  partitions?: string;
  /** Optional additional parts */
  extraParts?: FirmwarePart[];
  /** Optional SHA-256 hashes */
  checksums?: {
    app?: string;
    bootloader?: string;
    partitions?: string;
  };
}

export interface FirmwareManifest {
  name?: string;
  chipset?: string;
  releases: FirmwareRelease[];
}

export type FirmwareTrack = 'signed' | 'arbitrary';
export type FlashTargetMode = 'app_only' | 'migration';
export type ArbitrarySource = 'manifest' | 'local';
