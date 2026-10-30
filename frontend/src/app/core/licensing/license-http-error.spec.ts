import { HttpErrorResponse } from '@angular/common/http';
import { getLicenseHttpError } from './license-http-error';

describe('getLicenseHttpError', () => {

  it('reconhece erro RESTRICTED retornado pelo backend', () => {
    const error = new HttpErrorResponse({
      status: 403,
      error: {
        licenseMode: 'RESTRICTED',
        error: {
          code: 'LICENSE_OFFLINE_GRACE_EXPIRED',
          message:
            'A tolerância offline terminou. Regularize a comunicação com o License Server.'
        }
      }
    });

    expect(getLicenseHttpError(error)).toEqual({
      code: 'LICENSE_OFFLINE_GRACE_EXPIRED',
      message:
        'A tolerância offline terminou. Regularize a comunicação com o License Server.',
      licenseMode: 'RESTRICTED'
    });
  });

  it('reconhece código LICENSE_ mesmo sem modo RESTRICTED', () => {
    const error = new HttpErrorResponse({
      status: 403,
      error: {
        licenseMode: 'GRACE',
        error: {
          code: 'LICENSE_SIGNATURE_INVALID',
          message: 'Assinatura da licença inválida.'
        }
      }
    });

    expect(getLicenseHttpError(error)?.code)
      .toBe('LICENSE_SIGNATURE_INVALID');
  });

  it('não trata um 403 comum como erro de licença', () => {
    const error = new HttpErrorResponse({
      status: 403,
      error: {
        message: 'Acesso negado.'
      }
    });

    expect(getLicenseHttpError(error)).toBeNull();
  });

});
