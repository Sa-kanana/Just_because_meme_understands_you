/**
 * Resolve the browser loading mode for an image component.
 * @param {boolean} lazy
 * @returns {'lazy'|'eager'}
 */
export function resolveImageLoadingMode(lazy = true) {
  return lazy ? 'lazy' : 'eager'
}
