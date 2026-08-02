import tensorflow as tf

interpreter = tf.lite.Interpreter(model_path=r'c:\Users\mdsal\AndroidStudioProjects\Medisense\app\src\main\assets\DiseasePredictionModel.tflite')
interpreter.allocate_tensors()

input_details = interpreter.get_input_details()
output_details = interpreter.get_output_details()

print("Input Details:")
for detail in input_details:
    print(f"Name: {detail['name']}, Shape: {detail['shape']}, Type: {detail['dtype']}")

print("\nOutput Details:")
for detail in output_details:
    print(f"Name: {detail['name']}, Shape: {detail['shape']}, Type: {detail['dtype']}")
