export interface MenuItem {
  key: string;
  label: string;
  icon: string;
  route?: string[];
  open?: boolean;
  submenu?: MenuItem[];
}

