import os
import json
from datetime import datetime
import re

def datetime_serializer(obj):
    if isinstance(obj, datetime):
        return obj.isoformat()
    raise TypeError(f"Type {type(obj)} not serializable")

def datetime_deserializer(data):
    """
    Recursively converts ISO 8601 datetime strings back to datetime objects.
    """
    if isinstance(data, dict):
        return {key: datetime_deserializer(value) for key, value in data.items()}
    elif isinstance(data, list):
        return [datetime_deserializer(item) for item in data]
    elif isinstance(data, str):
        try:
            return datetime.fromisoformat(data)
        except ValueError:
            return data
    else:
        return data

class FileDatabase:
    def __init__(self, directory: str):
        if not os.path.isdir(directory):
            raise ValueError(f"Directory does not exist: {directory}")
        self.directory = directory

    def save(self, key: str, data: dict):
        file_path = os.path.join(self.directory, f"{key}.json")
        with open(file_path, "w") as file:
            json.dump(data, file, default=datetime_serializer)

    def load(self, key: str) -> dict:
        file_path = os.path.join(self.directory, f"{key}.json")
        if not os.path.isfile(file_path):
            raise FileNotFoundError(f"No such file: {file_path}")
        with open(file_path, "r") as file:
            data = json.load(file)
            return datetime_deserializer(data)
        
    def scan(self) -> list:
        """Scan the directory for files matching the pattern 'all numbers and end with .json'"""
        pattern = re.compile(r"^(\d+)\.json$")
        return [
            pattern.match(filename).group(1) for filename in os.listdir(self.directory)
            if pattern.match(filename)
        ]

    def delete(self, key: str) -> None:
        """Delete a file by key (removes the file from the directory)"""
        file_path = os.path.join(self.directory, f"{key}.json")
        if os.path.isfile(file_path):
            os.remove(file_path)
            print(f"Deleted file: {file_path}")
        else:
            raise FileNotFoundError(f"No such file to delete: {file_path}")