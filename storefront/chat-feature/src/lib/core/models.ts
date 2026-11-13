/** Types mirroring the OCC *WsDTO beans (tools/beangen/beans.json). The browser renders validated blocks only. */

export interface ProductCard
{
  code: string;
  name: string;
  imageUrl?: string;
  priceFormatted?: string;
  stockLabel?: string;
  stockIsDemo?: boolean;
  reasonSlots?: Record<string, string>;
}

export interface ComparisonRow { attributeLabel: string; values: string[]; }
export interface Comparison { columns: string[]; rows: ComparisonRow[]; notes?: string[]; }

export interface OrderSummary
{
  code: string;
  placedOn: string;
  statusLabel: string;
  totalFormatted: string;
  trackingReference?: string;
  statusSource?: string;
}

export interface PurchaseListLine
{
  productCode: string;
  name?: string;
  quantity: number;
  note?: string;
  origin?: string;
  lineFormatted?: string;
  issue?: string;
}

export interface Recommendation { code: string; name?: string; quantity?: number; reasonKey: string; estimate?: boolean; }
export interface WelcomeItem { kind: string; textKey: string; params?: Record<string, string>; }

export type BlockType =
  | 'text' | 'productCards' | 'comparison' | 'orderSummary' | 'purchaseList'
  | 'recommendationList' | 'welcome' | 'clarification' | 'notice';

export interface ChatBlock
{
  blockId: string;
  type: BlockType;
  text?: string;
  noticeKey?: string;
  productCards?: ProductCard[];
  comparison?: Comparison;
  orderSummaries?: OrderSummary[];
  purchaseListLines?: PurchaseListLine[];
  recommendations?: Recommendation[];
  welcomeItems?: WelcomeItem[];
  clarification?: string[];
  estimate?: boolean;
}

export interface TraceStep { label: string; source?: string; window?: string; threshold?: string; }

export interface ChatResponse
{
  responseId: string;
  language?: string;
  blocks: ChatBlock[];
  suggestedActions?: string[];
  feedbackHandle?: string;
  traceRef?: string;
  notices?: string[];
  trace?: TraceStep[];
}

export type StreamEventName = 'step_started' | 'step_completed' | 'block' | 'text_delta' | 'done' | 'error';

export interface ChatStreamEvent
{
  event: StreamEventName;
  stepId?: string;
  labelKey?: string;
  status?: string;
  block?: ChatBlock;
  delta?: string;
  code?: string;
  responseId?: string;
  trace?: TraceStep[];
}

export interface ContextTurn { role: 'user' | 'assistant'; text: string; }

export interface ChatRequest
{
  message: string;
  clientRequestId: string;
  uiLocale: string;
  context: { turns: ContextTurn[]; shortlistCodes: string[]; schemaVersion: number };
  purchaseList: { productCode: string; quantity: number; note?: string; origin?: string }[];
}

export interface PublicConfig
{
  enabledCapabilities: string[];
  activePack: string;
  interestNoticeText?: string;
  interestEventsEnabled?: boolean;
  stockDemoNotice?: string;
  purchaseListLabel?: string;
  samplePrompts?: string[];
  terminology?: Record<string, string>;
  themeTokens?: Record<string, string>;
  maxMessageLength?: number;
  contactText?: string;
}
