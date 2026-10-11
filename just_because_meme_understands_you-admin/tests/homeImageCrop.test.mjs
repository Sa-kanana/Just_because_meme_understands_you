import assert from 'node:assert/strict'
import test from 'node:test'
import {
  calculateContainRect,
  calculateCropScale,
  calculateOutputDimensions,
  calculateSourceCrop,
  clampImageOffset,
  isWholeImageCrop,
} from '../src/utils/homeImageCrop.mjs'

test('scales a 3:2 source to cover a 16:9 frame and applies zoom', () => {
  assert.equal(calculateCropScale(1200, 800, 640, 360, 1.5), 0.8)
})

test('clamps image movement so the crop frame stays fully covered', () => {
  assert.deepEqual(
    clampImageOffset(20, -500, 800, 600, 640, 360),
    { x: 0, y: -240 },
  )
})

test('maps the preview frame back to the matching source image rectangle', () => {
  assert.deepEqual(
    calculateSourceCrop(-80, -40, 0.8, 640, 360),
    { x: 100, y: 50, width: 800, height: 450 },
  )
})

test('fits a portrait crop fully inside the homepage banner preview', () => {
  assert.deepEqual(
    calculateContainRect(600, 1200, 480, 270),
    { x: 172.5, y: 0, width: 135, height: 270 },
  )
})

test('limits output size without forcing a fixed aspect ratio', () => {
  assert.deepEqual(calculateOutputDimensions(4000, 2000, 1920), { width: 1920, height: 960 })
  assert.deepEqual(calculateOutputDimensions(1000, 3000, 1920), { width: 640, height: 1920 })
})

test('recognizes the unmodified original so it can upload without re-encoding', () => {
  assert.equal(isWholeImageCrop({ x: 0.1, y: 0.1, width: 1600, height: 900 }, 1600, 900), true)
  assert.equal(isWholeImageCrop({ x: 0, y: 0, width: 1200, height: 900 }, 1600, 900), false)
})
