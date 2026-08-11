export type V2ProductionItem = {
  productionContext:
    | 'INITIAL'
    | 'CYTOLOGY'
    | 'FROZEN_ROUND'
    | 'TECHNICAL_ORDER'
    | 'EXTERNAL'
    | string;
  caseId: string;
  pathologyNo: string;
  patientReference: string;
  businessTypeCode?: string | null;
  businessTypeName?: string | null;
  materialSummary: string;
  taskSummary: string;
  requiredCount: number;
  completedCount: number;
  enteredAt: string;
  waitingMinutes: number;
  currentOperator: string | null;
  deepLink: string;
  availableActions: string[];
  orderId?: string | null;
  orderNo?: string | null;
  productionContextId?: string | null;
  slideCode?: string | null;
  slideType?: string | null;
};

export type V2ProductionQueue = {
  code: string;
  label: string;
  count: number;
  items: V2ProductionItem[];
};

export type V2ProductionWorkbench = {
  refreshedAt: string;
  queues: {
    routineProduction: V2ProductionQueue;
    cytologyProduction: V2ProductionQueue;
    frozenProduction: V2ProductionQueue;
    technicalOrders: V2ProductionQueue;
    incompleteSlides: V2ProductionQueue;
    exceptions: V2ProductionQueue;
  };
};

const emptyQueue = (code: string, label: string): V2ProductionQueue => ({
  code,
  label,
  count: 0,
  items: [],
});

export async function getV2ProductionWorkbench(): Promise<V2ProductionWorkbench> {
  const response = await fetch('/api/v2/production-workbench');
  const body = (await response.json()) as {
    refreshedAt?: string;
    queues?: Partial<V2ProductionWorkbench['queues']>;
    message?: string;
  };
  if (!response.ok) throw new Error(body.message ?? '生产工作台暂时无法加载');
  const queues = body.queues ?? {};
  return {
    refreshedAt: body.refreshedAt ?? new Date().toISOString(),
    queues: {
      routineProduction: queues.routineProduction ?? emptyQueue('ROUTINE_PRODUCTION', '常规制片'),
      cytologyProduction:
        queues.cytologyProduction ?? emptyQueue('CYTOLOGY_PRODUCTION', '细胞制片'),
      frozenProduction: queues.frozenProduction ?? emptyQueue('FROZEN_PRODUCTION', '冰冻制片'),
      technicalOrders: queues.technicalOrders ?? emptyQueue('TECHNICAL_ORDER', '技术医嘱'),
      incompleteSlides: queues.incompleteSlides ?? emptyQueue('INCOMPLETE_SLIDES', '待完成玻片'),
      exceptions: queues.exceptions ?? emptyQueue('EXCEPTIONS', '异常 / 返工'),
    },
  };
}
