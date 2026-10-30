/** Resposta crua da BrasilAPI (https://brasilapi.com.br/api/banks/v1). */
export interface BrasilApiBank {
  ispb: string;
  name: string | null;
  code: number | null;
  fullName: string | null;
}

/** Banco já normalizado para uso na tela. */
export interface Bank {
  /** Código COMPE com 3 dígitos (ex.: "341"). */
  code: string;
  name: string;
  fullName: string;
  /** Texto gravado no cadastro (ex.: "341 - ITAÚ UNIBANCO S.A."). */
  label: string;
}
