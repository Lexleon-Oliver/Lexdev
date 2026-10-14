import { PersonResponseDto } from './person-response-dto';

export interface SupplierSummary {
  id: number;
  person: PersonResponseDto;
  email?: string | null;
  phone?: string | null;
  categoria?: string | null;
}
