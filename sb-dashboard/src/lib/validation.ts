const SNOWFLAKE_RE = /^\d{17,20}$/;

export function isValidSnowflake(id: string): boolean {
  return SNOWFLAKE_RE.test(id);
}
