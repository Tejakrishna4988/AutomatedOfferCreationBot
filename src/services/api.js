import axios from 'axios';

const API_BASE_URL = 'http://localhost:8080/api';

export const extractFromText = async (text) => {
  try {
    const response = await axios.post(`${API_BASE_URL}/offer/extractText`, text, {
      headers: {
        'Content-Type': 'text/plain'
      }
    });
    return response.data;
  } catch (error) {
    throw error.response?.data || error;
  }
};

export const extractFromFile = async (file) => {
  const formData = new FormData();
  formData.append('file', file);

  try {
    const response = await axios.post(`${API_BASE_URL}/extractCsv`, formData, {
      headers: {
        'Content-Type': 'multipart/form-data'
      }
    });
    return response.data;
  } catch (error) {
    throw error.response?.data || error;
  }
}; 