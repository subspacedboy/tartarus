export interface EfuseStatus {
  blk3_provisioned: boolean;
  blk3_write_protected: boolean;
  secure_boot_enabled: boolean;
  pad_jtag_disabled: boolean;
  usb_jtag_disabled: boolean;
  soft_jtag_disabled: boolean;
  security_download_enabled: boolean;
  usb_download_disabled: boolean;
  secure_version: number;
}

export interface ProvisionedHardwareUnitSummary {
  hardwareId: string;
  provisioningMode: 'HARD' | 'SOFT' | string;
  label: string | null;
  initialSessionToken: string;
  lastKnownSessionToken: string | null;
  lastFirmwareVersion: string | null;
  lastAttestedAt: string | null;
  isRevoked: boolean;
  provisionedAt: string;
  updatedAt: string;
}

export interface HardwareAttestationLogItem {
  id: number;
  hardwareId: string | null;
  sessionToken: string;
  firmwareVersion: string | null;
  verificationMethod: string;
  success: boolean;
  failureReason: string | null;
  createdAt: string;
}

export interface ProvisionedHardwareUnitDetail {
  hardwareId: string;
  provisioningMode: 'HARD' | 'SOFT' | string;
  authPubkeyB64: string;
  manufacturerSigB64: string;
  attestationDigestHex: string;
  infineonCertsDerB64: string[];
  leafCertSubject: string;
  leafCertIssuer: string;
  leafCertSerialHex: string;
  blk3Provisioned: boolean;
  blk3WriteProtected: boolean;
  secureBootEnabled: boolean;
  padJtagDisabled: boolean;
  usbJtagDisabled: boolean;
  softJtagDisabled: boolean;
  securityDownloadEnabled: boolean;
  usbDownloadDisabled: boolean;
  secureVersion: number;
  initialSessionToken: string;
  lastKnownSessionToken: string | null;
  lastKnownLockPubkeyB64: string | null;
  lastFirmwareVersion: string | null;
  lastAttestedAt: string | null;
  label: string | null;
  notes: string | null;
  isRevoked: boolean;
  revokedReason: string | null;
  provisionedAt: string;
  updatedAt: string;
  attestationLogs: HardwareAttestationLogItem[];
}

export interface ProvisioningPreviewResult {
  valid: boolean;
  provisioningMode: string;
  hardwareId: string;
  sessionToken: string;
  leafCertSubject: string;
  leafCertIssuer: string;
  leafCertSerialHex: string;
  chainValid: boolean;
  attestationDigestValid: boolean;
  signatureValid: boolean;
  efuseStatus: EfuseStatus | null;
  warnings: string[];
  errors: string[];
}

export interface ProvisioningIngestRequest {
  rawPayload?: string;
  label?: string;
  notes?: string;
}

export interface UpdateHardwareUnitRequest {
  label?: string | null;
  notes?: string | null;
  isRevoked?: boolean | null;
  revokedReason?: string | null;
}
