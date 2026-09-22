import { TipoConta } from "./tipo-conta";

export interface BankDetailsDto {
  banco: string;
  agencia: string;
  conta: string;
  tipoConta: TipoConta;
  chavePix?: string;
}
