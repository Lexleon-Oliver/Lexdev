import { ContactType } from "./contact-type";

export interface PersonContact {
  id?: number;
  type: ContactType;
  value: string;
  principal: boolean;
  description?: string;
}
