import { fiscalActionsFor, Sale } from './sale';

function sale(status: Sale['status'], fiscalStatus: NonNullable<Sale['fiscalDocument']>['status'], emissionType: NonNullable<Sale['fiscalDocument']>['emissionType'], accessKey: string | null = null): Sale {
  return {
    id: 1, fiscalEstablishmentId: 1, userId: 1, status, saleAt: '2026-10-08T12:00:00',
    subtotal: 10, discount: 0, total: 10, totalPaid: 10, change: 0, items: [], payments: [],
    fiscalDocument: { id: 1, model: '65', series: 1, number: 1, status: fiscalStatus, emissionType, accessKey },
  };
}

describe('fiscalActionsFor', () => {
  it('allows normal issue or deliberate offline preparation only before transmission', () => {
    const actions = fiscalActionsFor(sale('AGUARDANDO_FISCAL', 'AGUARDANDO_AUTORIZACAO', 'NORMAL'));
    expect(actions.issueNormal).toBe(true);
    expect(actions.prepareOfflineContingency).toBe(true);
    expect(actions.transmitOfflineContingency).toBe(false);
    expect(actions.consult).toBe(false);
  });

  it('allows only contingency transmission and documents for prepared offline NFC-e', () => {
    const actions = fiscalActionsFor(sale('FISCAL_PENDENTE', 'CONTINGENCIA', 'CONTINGENCIA_OFFLINE', 'KEY'));
    expect(actions.issueNormal).toBe(false);
    expect(actions.prepareOfflineContingency).toBe(false);
    expect(actions.transmitOfflineContingency).toBe(true);
    expect(actions.downloadDanfe).toBe(true);
    expect(actions.downloadXml).toBe(true);
    expect(actions.consult).toBe(false);
  });

  it('allows consultation but never retransmission for uncertain result', () => {
    const actions = fiscalActionsFor(sale('FISCAL_PENDENTE', 'PENDENTE_CONSULTA', 'NORMAL', 'KEY'));
    expect(actions.consult).toBe(true);
    expect(actions.transmitOfflineContingency).toBe(false);
    expect(actions.prepareOfflineContingency).toBe(false);
  });

  it('allows DANFE XML and cancellation only for authorized sale', () => {
    const actions = fiscalActionsFor(sale('FISCALIZADA', 'AUTORIZADA', 'NORMAL', 'KEY'));
    expect(actions.downloadDanfe).toBe(true);
    expect(actions.downloadXml).toBe(true);
    expect(actions.cancel).toBe(true);
    expect(actions.consult).toBe(false);
  });

  it('offers no fiscal transition for rejected or cancelled documents', () => {
    expect(Object.values(fiscalActionsFor(sale('FISCAL_REJEITADA', 'REJEITADA', 'NORMAL'))).some(Boolean)).toBe(false);
    expect(Object.values(fiscalActionsFor(sale('CANCELADA', 'CANCELADA', 'NORMAL', 'KEY'))).some(Boolean)).toBe(false);
  });
});
