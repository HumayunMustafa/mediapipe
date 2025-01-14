"""Unit tests for pytorch_converter."""

import os

from absl.testing import absltest
from absl.testing import parameterized

from mediapipe.tasks.python.test import test_utils
from mediapipe.tasks.tools import pytorch_converter

_TEST_DATA_DIR = 'mediapipe/tasks/testdata/text'
_PYTORCH_FILE = test_utils.get_test_data_path(
    os.path.join(_TEST_DATA_DIR, 'falcon_rw_1b_test_weight.pt')
)


class PytorchConverterTest(parameterized.TestCase):
  VARIABLE_NAMES = [
      'transformer.word_embeddings.weight',
      'transformer.h.0.input_layernorm.weight',
      'transformer.h.0.input_layernorm.bias',
      'transformer.h.0.self_attention.query_key_value.weight',
      'transformer.h.0.self_attention.query_key_value.bias',
      'transformer.h.0.self_attention.dense.weight',
      'transformer.h.0.self_attention.dense.bias',
      'transformer.h.0.post_attention_layernorm.weight',
      'transformer.h.0.post_attention_layernorm.bias',
      'transformer.h.0.mlp.dense_h_to_4h.weight',
      'transformer.h.0.mlp.dense_h_to_4h.bias',
      'transformer.h.0.mlp.dense_4h_to_h.weight',
      'transformer.h.0.mlp.dense_4h_to_h.bias',
      'transformer.ln_f.weight',
      'transformer.ln_f.bias',
      'lm_head.weight',
  ]

  def test_init(self):
    loader = pytorch_converter.PytorchCkptLoader(
        ckpt_path=_PYTORCH_FILE,
        is_symmetric=True,
        attention_quant_bits=8,
        feedforward_quant_bits=8,
        embedding_quant_bits=8,
        special_model='FALCON_RW_1B',
    )
    self.assertEqual(loader._ckpt_path, _PYTORCH_FILE)
    self.assertEqual(loader._is_symmetric, True)
    self.assertEqual(loader._attention_quant_bits, 8)
    self.assertEqual(loader._feedforward_quant_bits, 8)

  @parameterized.product(
      quant_bits=(4, 8),
  )
  def test_load_to_actions(self, quant_bits):
    loader = pytorch_converter.PytorchCkptLoader(
        ckpt_path=_PYTORCH_FILE,
        is_symmetric=True,
        attention_quant_bits=8,
        feedforward_quant_bits=quant_bits,
        embedding_quant_bits=8,
        special_model='FALCON_RW_1B',
    )
    actions = loader.load_to_actions()
    # There are 16 layers in the model, but qkv weight and bias would be
    # decomposed to q, k, v tensors, so there would be 20 quantization actions.
    self.assertLen(actions, 20)


if __name__ == '__main__':
  absltest.main()
