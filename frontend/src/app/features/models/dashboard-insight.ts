export type DashboardInsightStatus =
  | 'GENERATED'
  | 'DISABLED'
  | 'UNAVAILABLE';

export interface DashboardInsight {
  status: DashboardInsightStatus;
  insight: string;
}
