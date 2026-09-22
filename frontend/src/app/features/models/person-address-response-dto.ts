import { TipoEndereco } from "./tipo-endereco";

export interface PersonAddressResponseDto {
  id: number;
  type: TipoEndereco;
  cep: string;
  logradouro: string;
  numero: string;
  complemento?: string;
  bairro: string;
  cidade: string;
  uf: string;
  principal: boolean;
}
