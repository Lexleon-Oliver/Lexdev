import { TipoPessoa } from "./tipo-pessoa";

export interface PersonResponseDto {
  id: number;
  tipoPessoa: TipoPessoa;
  name: string;
  cpfCnpj: string;
}
