import type { ReactNode } from "react";
import { AlertTriangle, CheckCircle2, CircleSlash, Loader2, SearchX } from "lucide-react";
import { compactJson } from "../lib/format";

export function LoadingState({ label = "Loading" }: { label?: string }) {
  return (
    <div className="state state-loading" role="status">
      <Loader2 size={18} aria-hidden="true" />
      <span>{label}</span>
    </div>
  );
}

export function EmptyState({ title, detail }: { title: string; detail?: string }) {
  return (
    <div className="state">
      <SearchX size={20} aria-hidden="true" />
      <div>
        <strong>{title}</strong>
        {detail ? <p>{detail}</p> : null}
      </div>
    </div>
  );
}

export function AccessDenied({ message }: { message?: string }) {
  return (
    <div className="state state-denied" role="alert">
      <CircleSlash size={20} aria-hidden="true" />
      <div>
        <strong>Access denied</strong>
        <p>{message || "The backend rejected this request for the current identity."}</p>
      </div>
    </div>
  );
}

export function ErrorState({ message }: { message: string }) {
  return (
    <div className="state state-error" role="alert">
      <AlertTriangle size={20} aria-hidden="true" />
      <span>{message}</span>
    </div>
  );
}

export function StatusBadge({ value }: { value: string | boolean }) {
  const text = typeof value === "boolean" ? (value ? "Enabled" : "Disabled") : value;
  const normalized = text.toLowerCase();
  const tone =
    normalized.includes("passed") ||
    normalized.includes("active") ||
    normalized.includes("enabled") ||
    normalized.includes("succeeded") ||
    normalized.includes("published")
      ? "positive"
      : normalized.includes("failed") ||
          normalized.includes("blocked") ||
          normalized.includes("denied") ||
          normalized.includes("disabled") ||
          normalized.includes("deleted")
        ? "negative"
        : normalized.includes("pending") || normalized.includes("proposed") || normalized.includes("confirmation")
          ? "warning"
          : "neutral";
  return <span className={`badge badge-${tone}`}>{text}</span>;
}

export function SectionHeader({
  title,
  actions,
  eyebrow
}: {
  title: string;
  eyebrow?: string;
  actions?: ReactNode;
}) {
  return (
    <div className="section-header">
      <div>
        {eyebrow ? <span className="eyebrow">{eyebrow}</span> : null}
        <h2>{title}</h2>
      </div>
      {actions ? <div className="section-actions">{actions}</div> : null}
    </div>
  );
}

export function IconButton({
  children,
  title,
  disabled,
  onClick,
  variant = "ghost",
  type = "button"
}: {
  children: ReactNode;
  title: string;
  disabled?: boolean;
  onClick?: () => void;
  variant?: "ghost" | "primary" | "danger";
  type?: "button" | "submit";
}) {
  return (
    <button
      className={`icon-button icon-button-${variant}`}
      type={type}
      aria-label={title}
      title={title}
      disabled={disabled}
      onClick={onClick}
    >
      {children}
    </button>
  );
}

export function TextField({
  label,
  value,
  onChange,
  placeholder,
  type = "text"
}: {
  label: string;
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
  type?: string;
}) {
  return (
    <label className="field">
      <span>{label}</span>
      <input type={type} value={value} placeholder={placeholder} onChange={event => onChange(event.target.value)} />
    </label>
  );
}

export function TextAreaField({
  label,
  value,
  onChange,
  rows = 4
}: {
  label: string;
  value: string;
  onChange: (value: string) => void;
  rows?: number;
}) {
  return (
    <label className="field">
      <span>{label}</span>
      <textarea value={value} rows={rows} onChange={event => onChange(event.target.value)} />
    </label>
  );
}

export function ToggleField({
  label,
  checked,
  onChange
}: {
  label: string;
  checked: boolean;
  onChange: (checked: boolean) => void;
}) {
  return (
    <label className="toggle-field">
      <input type="checkbox" checked={checked} onChange={event => onChange(event.target.checked)} />
      <span>{label}</span>
    </label>
  );
}

export function JsonPreview({ value }: { value: unknown }) {
  return <pre className="json-preview">{compactJson(value)}</pre>;
}

export function SuccessInline({ children }: { children: ReactNode }) {
  return (
    <span className="inline-success">
      <CheckCircle2 size={14} aria-hidden="true" />
      {children}
    </span>
  );
}
