import { HttpErrorResponse } from '@angular/common/http';

export interface LicenseHttpError {
  code: string;
  message: string;
  licenseMode: string | null;
}

type JsonRecord = Record<string, unknown>;

const asRecord = (value: unknown): JsonRecord | null =>
  value !== null && typeof value === 'object'
    ? value as JsonRecord
    : null;

const asNonBlankString = (value: unknown): string | null =>
  typeof value === 'string' && value.trim().length > 0
    ? value.trim()
    : null;

export function getLicenseHttpError(
  error: HttpErrorResponse
): LicenseHttpError | null {

  const payload = asRecord(error.error);

  if (!payload) {
    return null;
  }

  const nestedError = asRecord(payload['error']);

  if (!nestedError) {
    return null;
  }

  const code =
    asNonBlankString(nestedError['code']);

  const message =
    asNonBlankString(nestedError['message']);

  const licenseMode =
    asNonBlankString(payload['licenseMode']);

  const isLicenseError =
    licenseMode === 'RESTRICTED' ||
    code?.startsWith('LICENSE_') === true;

  if (!isLicenseError) {
    return null;
  }

  return {
    code: code ?? 'LICENSE_RESTRICTED',
    message:
      message ??
      'A operação está bloqueada pelo licenciamento do SystemPro.',
    licenseMode
  };
}
