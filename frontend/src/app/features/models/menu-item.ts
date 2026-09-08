export interface MenuItem {
  label: string;
  icon: string;
  route: string | null;
  children: MenuItem[];
  expanded?: boolean;
}

