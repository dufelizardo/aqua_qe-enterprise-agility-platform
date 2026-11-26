/**
 * Paletas do editor e do ícone da página (#86). As cores são fixas, em #rrggbb, como o servidor exige; o destaque usa
 * tons claros, e o texto destacado fica escuro nos dois temas (estilo `.rich-content mark`).
 */
export interface Swatch {
  label: string;
  color: string;
}

export const TEXT_COLORS: Swatch[] = [
  { label: 'Cinza', color: '#6b7280' },
  { label: 'Vermelho', color: '#b42318' },
  { label: 'Laranja', color: '#c4320a' },
  { label: 'Verde', color: '#067647' },
  { label: 'Azul', color: '#175cd3' },
  { label: 'Roxo', color: '#6941c6' },
];

export const HIGHLIGHTS: Swatch[] = [
  { label: 'Amarelo', color: '#fef08a' },
  { label: 'Verde', color: '#bbf7d0' },
  { label: 'Azul', color: '#bfdbfe' },
  { label: 'Rosa', color: '#fbcfe8' },
  { label: 'Laranja', color: '#fed7aa' },
];

/** Emojis do editor e do ícone da página: um conjunto curto, pensado para documentação de trabalho. */
export const EMOJIS: string[] = [
  '📄', '📘', '📌', '📎', '📝', '📋', '📊', '📈', '🗂️', '🗓️',
  '✅', '☑️', '❌', '⚠️', '❗', '❓', '💡', '🔒', '🔑', '🛡️',
  '🚀', '🎯', '🏁', '⭐', '🔥', '⏳', '🔧', '⚙️', '🧪', '🐞',
  '👥', '🤝', '💬', '📣', '🏢', '🌐', '💰', '📦', '🧭', '🏆',
];

/** Cor válida para o servidor: #rrggbb. */
export function isHexColor(value: string): boolean {
  return /^#[0-9a-fA-F]{6}$/.test(value);
}
