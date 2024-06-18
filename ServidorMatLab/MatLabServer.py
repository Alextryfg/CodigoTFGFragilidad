from flask import Flask, request, jsonify
import matlab.engine
import os
from werkzeug.utils import secure_filename
import pandas as pd
import numpy as np

app = Flask(__name__)
eng = matlab.engine.start_matlab()

UPLOAD_FOLDER = 'uploads'  # Directory to save the files
os.makedirs(UPLOAD_FOLDER, exist_ok=True)  # Create the directory if it doesn't exist
SCRIPT_FOLDER = r'C:\Users\alexc\Desktop\uploads'  # Directory where your MATLAB script is located

def is_number(s):
    try:
        float(s)
        return True
    except ValueError:
        return False

@app.route('/process_csv', methods=['POST'])
def process_csv():
    if 'file' not in request.files or 'leg_length' not in request.form:
        return jsonify({'error': 'No file part or leg length provided'}), 400
    file = request.files['file']
    if file.filename == '':
        return jsonify({'error': 'No selected file'}), 400
    leg_length = float(request.form['leg_length'])
    
    if file:
        filename = secure_filename(file.filename)
        filepath = os.path.join(UPLOAD_FOLDER, filename)
        file.save(filepath)

        # Read the CSV file using pandas
        df = pd.read_csv(filepath)

        # Convert the data to decimal format
        df = df.applymap(lambda x: f"{float(x):.10f}" if isinstance(x, (float, int)) else (f"{float(x):.10f}" if is_number(x) else x))

        # Explicitly convert cells to numbers
        df = df.applymap(lambda x: float(x) if is_number(x) else x)

        # Define the new Excel filename
        new_filename = filename.rsplit('.', 1)[0] + '_processed.xlsx'
        new_filepath = os.path.join(UPLOAD_FOLDER, new_filename)

        # Ensure the file is not locked or in use
        if os.path.exists(new_filepath):
            os.remove(new_filepath)

        # Save the DataFrame as an Excel file
        try:
            df.to_excel(new_filepath, index=False, engine='openpyxl')
        except PermissionError:
            return jsonify({'error': 'Permission denied. The file might be in use.'}), 500

        # Execute the MATLAB script
        try:
            eng.addpath(SCRIPT_FOLDER)  # Add the MATLAB script path
            results = eng.process_accelerometer_data(new_filepath, leg_length)
        except Exception as e:
            return jsonify({'error': str(e)}), 500

        # Check if the results indicate that the phone is in rest
        if np.isnan(results['gait_velocity']):
            print('It seems the phone is at rest. No significant motion detected.')
            return jsonify({'message': 'It seems the phone is at rest. No significant motion detected.'}), 200

        # Convert the MATLAB results to a JSON-serializable format
        results_dict = {
            'gait_velocity': round(float(results['gait_velocity']), 2),
            'num_steps': int(results['num_steps']),
            'distance_traveled': round(float(results['distance_traveled']), 2),
            'mean_step_length': round(float(results['mean_step_length']), 2)
        }

        return jsonify(results_dict), 200

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000)
