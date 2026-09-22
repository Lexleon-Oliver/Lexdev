import { TipoContato } from "./tipo-contato";

export interface PersonContactResponseDto {
  id: number;
  type: TipoContato;
  value: string;
  principal: boolean;
  description?: string;
}
